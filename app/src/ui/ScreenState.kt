package frameluma.app.ui

import android.content.Context
import android.os.Build
import frameluma.app.R
import frameluma.core.FramePacer
import frameluma.runtime.AppCore
import frameluma.runtime.Devices
import frameluma.runtime.LayoutOptions
import org.json.JSONObject

/**
 * Controls, equipment and preview semantics. A new value means a key press, a saved setting or a
 * connection/follow change; measured intervals and frame colours travel in their own flows.
 */
data class ScreenState(
    val enabled: Boolean = false,
    val brightness: Int = 0,
    val speed: Int = FramePacer.DEFAULT_SPEED_INDEX,
    val layout: LayoutOptions? = null,
    val connection: String = "",
    val connected: Boolean = false,
    val following: Boolean = false,
    val message: String = "",
    val lightName: String = "",
    val lightModel: String = "",
    val device: String = Build.MODEL.orEmpty(),
    val source: String = "",
    val edgeDepth: Int = 0,
)

/** Latest measured output and sweep intervals in milliseconds, unsmoothed; 0 means none. */
data class Timings(val outputMs: Int = 0, val sweepMs: Int = 0) {
    val measured: Boolean
        get() = outputMs > 0 || sweepMs > 0

    companion object {
        val NONE = Timings()

        fun text(ms: Int) = if (ms > 0) "$ms ms" else "— ms"
    }
}

/** Screen-side status pacing. Capture, light output and health reports keep their own cadence. */
internal object StatusPolicy {
    /** At most one timing text change per interval; the newest value is the one shown. */
    const val TIMING_INTERVAL_MS = 500L

    /**
     * A due timing value normally joins the next preview colour frame, so it costs no frame of its
     * own. This timer covers moments without colour samples; it waits past the slowest sampling.
     */
    const val TIMING_FALLBACK_MS = UiCapture.NAVIGATION_PERIOD_MS + 50L

    /** Notifications arriving together share one read. */
    const val COALESCE_MS = 48L

    /** A report older than this no longer proves a live connection. */
    const val REPORT_FRESH_MS = 5000L
}

/** Settings, USB presence and model policy. Re-read only after the configuration token changes. */
internal class StatusSettings(
    val token: String,
    val enabled: Boolean,
    val brightness: Int,
    val speed: Int,
    val layout: LayoutOptions?,
    val present: Boolean,
    val edgeDepth: Int,
) {
    companion object {
        /** May enumerate USB devices and query packages; never call on the main thread. */
        fun read(context: Context, token: String) =
            StatusSettings(
                token,
                AppCore.enabled(),
                AppCore.brightness(),
                FramePacer.speedIndex(AppCore.period()),
                AppCore.layout(),
                Devices.lightPresent(context),
                Devices.capturePolicy(context)?.edgeDepthPercent ?: 0,
            )
    }
}

/** One engine report, parsed once for both the stable state and the measured intervals. */
internal class StatusReport(json: String, val at: Long) {
    val model: String
    val access: Boolean
    val unsupported: Boolean
    val error: String
    val lightReady: Boolean
    val running: Boolean
    val batches: Int
    val units: Int
    val moduleId: String
    val lightName: String?
    val ownSource: Boolean
    val backgroundRestricted: Boolean
    val outputMs: Int
    val sweepMs: Int

    init {
        val root = JSONObject(json)
        val engine = root.optJSONObject("engine") ?: JSONObject()
        model = engine.optString("light_model", "")
        access = root.optBoolean("requires_usb_permission")
        unsupported = engine.optBoolean("requires_configuration")
        error = engine.optString("error", "").ifEmpty { root.optString("error", "") }
        lightReady = engine.optBoolean("light_ready")
        running = engine.optBoolean("running")
        batches = engine.optInt("batches")
        units = engine.optInt("light_unit_count")
        moduleId = engine.optString("light_module_id", "")
        lightName = if (engine.has("light_name")) engine.optString("light_name") else null
        ownSource = engine.optString("capture_source") == "own_app"
        backgroundRestricted =
            root.has("background_service_allowed") && !root.optBoolean("background_service_allowed")
        outputMs = engine.optInt("output_interval_ms")
        sweepMs = engine.optInt("actual_period_ms")
    }

    fun fresh(now: Long) = now - at in 0..StatusPolicy.REPORT_FRESH_MS

    /** Milliseconds until [fresh] turns false; non-positive once it already has. */
    fun expiresIn(now: Long) = at + StatusPolicy.REPORT_FRESH_MS + 1 - now

    companion object {
        fun current() = StatusReport(AppCore.state(), AppCore.reportedAt())
    }
}

/** IO snapshot; time-dependent connection state is evaluated when the UI consumes it. */
internal class StatusSnapshot(
    val settings: StatusSettings,
    val report: StatusReport,
) {
    fun stateAt(context: Context, now: Long) = screenState(context, settings, report, now)

    companion object {
        fun read(settings: StatusSettings) = StatusSnapshot(settings, StatusReport.current())
    }
}

/** Pure interpretation of settings and one report; never opens a light transport. */
internal fun screenState(
    context: Context,
    settings: StatusSettings,
    report: StatusReport,
    now: Long,
): ScreenState {
    val present = settings.present
    val enabled = settings.enabled
    val identified = present && settings.layout != null && report.model.isNotBlank()
    val error = report.error
    val ready = identified && report.fresh(now) && report.lightReady && error.isEmpty()
    val following = enabled && ready && report.running && report.batches > 0
    val connection =
        context.getString(
            when {
                report.access -> R.string.connection_usb_permission
                report.unsupported -> R.string.connection_unsupported
                ready -> R.string.connection_connected
                present && enabled && error.isNotEmpty() -> R.string.connection_restoring
                present && enabled -> R.string.connection_identifying
                present -> R.string.connection_usb_detected
                else -> R.string.connection_disconnected
            }
        )
    val message =
        context.getString(
            when {
                !present -> R.string.message_waiting_connection
                !enabled -> R.string.message_disabled
                report.access -> R.string.message_waiting_usb
                report.unsupported -> R.string.message_unsupported
                error.isNotEmpty() -> R.string.message_restoring
                following -> R.string.message_syncing
                else -> R.string.message_ready
            }
        )
    val name =
        when {
            identified ->
                frameluma.runtime.LightConnections.localizedName(
                    context,
                    report.moduleId,
                    report.lightName ?: context.getString(R.string.light_name_fallback),
                ) +
                    if (report.units > 0) context.getString(R.string.units_suffix, report.units)
                    else ""
            present && enabled -> context.getString(R.string.light_identifying)
            present -> context.getString(R.string.light_enable_to_identify)
            else -> context.getString(R.string.light_connect_usb)
        }
    var source =
        context.getString(
            if (!following) R.string.source_waiting
            else if (report.ownSource) R.string.source_ui
            else R.string.source_display
        )
    if (report.backgroundRestricted) source += context.getString(R.string.source_background_restricted)
    return ScreenState(
        enabled,
        settings.brightness,
        settings.speed,
        settings.layout,
        connection,
        ready,
        following,
        message,
        name,
        if (identified) report.model else "",
        Build.MODEL.ifBlank { context.getString(R.string.device_unknown) },
        source,
        settings.edgeDepth,
    )
}

/** Measured intervals are only meaningful while following. */
internal fun timingsOf(report: StatusReport, following: Boolean) =
    if (following) Timings(report.outputMs.coerceAtLeast(0), report.sweepMs.coerceAtLeast(0))
    else Timings.NONE

/**
 * Bounded timing text: keeps only the newest value and releases at most one change per interval.
 * A measurement appearing or disappearing (start, stop, error, stale report) passes at once;
 * a new number waits for [release], which the caller times to an existing colour frame.
 */
internal class TimingGate(private val intervalMs: Long = StatusPolicy.TIMING_INTERVAL_MS) {
    var shown: Timings = Timings.NONE
        private set

    private var shownAt = 0L
    private var pending: Timings? = null

    /** Earliest release time of the waiting value, or null when nothing waits. */
    val dueAt: Long?
        get() = pending?.let { shownAt + intervalMs }

    /** True when [shown] changed now; otherwise the newest value waits for [release]. */
    fun offer(value: Timings, now: Long): Boolean {
        if (value == shown) {
            pending = null
            return false
        }
        if (value.measured != shown.measured) {
            show(value, now)
            return true
        }
        pending = value
        return false
    }

    /** Shows the newest waiting value once the interval since the last change has passed. */
    fun release(now: Long): Boolean {
        val value = pending ?: return false
        if (now - shownAt < intervalMs) return false
        show(value, now)
        return true
    }

    /** Drops a waiting value; the shown value stays as the comparison base. */
    fun cancel() {
        pending = null
    }

    private fun show(value: Timings, now: Long) {
        shown = value
        shownAt = now
        pending = null
    }
}
