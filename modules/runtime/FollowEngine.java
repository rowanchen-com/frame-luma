package frameluma.runtime;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import frameluma.core.*;
import frameluma.deviceapi.*;

import org.json.JSONArray;
import org.json.JSONObject;

/** One capture/output owner, bounded status, no per-frame disk writes. */
public final class FollowEngine {
    private static final String REVISION = "frame-luma-18";
    private static final Uri STATE = AppCore.STATE_URI;
    private static final long PREVIEW_INTERVAL_MS = 500;
    private final Context host;
    private final boolean localState;
    private volatile Thread worker;
    private volatile boolean running;
    private final FollowState progress = new FollowState();
    private volatile boolean lightReady;
    private volatile Options options;
    private static final java.util.concurrent.atomic.AtomicReferenceFieldUpdater<
                    FollowEngine, Options>
            OPTIONS_SLOT =
                    java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater(
                            FollowEngine.class, Options.class, "options");
    private String configurationRevision;
    private volatile int batches, sweeps, captureMs, cycleMs, pointCount;
    private volatile int outputIntervalMs, outputWriteMs, firstUpdateMs;
    private volatile LightOutput activeOutput;
    private long previousOutputAt, previousPreviewAt;
    private volatile boolean progressive;
    private volatile String captureSource = "device";
    private volatile String lightModel = "";
    private volatile int lightUnits;
    private final Object colorsLock = new Object();
    private final FramePacer.Waiter pacing = new FramePacer.Waiter();
    private int[] lastColors = new int[0];
    private volatile String lightModuleId = "", endpointId = "";
    private volatile String deviceName = "等待机型验证", deviceId = "", lightName = "";

    private static final class Options {
        final LayoutOptions layout;
        final int period, brightness, locatorUnit, locatorZone;
        final long until;
        final boolean ui;
        final String layoutKey;
        final String connectionKey;
        final String sessionKey, frameKey;
        final EdgeLayout geometry;
        final JSONObject layoutJson;

        Options(JSONObject json) throws Exception {
            connectionKey = json.getString("connection_key");
            layout = json.isNull("layout") ? null : new LayoutOptions(json.getJSONObject("layout"));
            layoutJson = layout == null ? null : layout.json();
            layoutKey = layout == null ? "pending" : layoutJson.toString();
            geometry = layout == null ? null : layout.geometry();
            period = FramePacer.validatePeriod(json.getInt("period_ms"));
            brightness = json.getInt("brightness_percent");
            if (brightness < 0 || brightness > 100) throw new Exception("Brightness out of range");
            locatorUnit = json.optInt("locator_unit", 0);
            locatorZone = json.optInt("locator_zone", -1);
            until = json.optLong("locator_until_elapsed", 0);
            ui = json.optBoolean("ui_visible", false);
            sessionKey =
                    connectionKey
                            + ":"
                            + (layout == null ? "pending" : layout.mode + ":" + layout.units);
            frameKey =
                    layoutKey
                            + ":"
                            + period
                            + ":"
                            + brightness
                            + ":"
                            + locatorUnit
                            + ":"
                            + locatorZone
                            + ":"
                            + until
                            + ":"
                            + ui;
            if (layout != null
                    && (locatorUnit < 0
                            || locatorUnit > layout.units
                            || locatorZone < -1
                            || locatorZone >= layout.counts.length
                            || (locatorUnit > 0 && locatorZone >= 0)))
                throw new Exception("Invalid locator");
        }

        boolean locating() {
            return layout != null
                    && until > SystemClock.elapsedRealtime()
                    && (locatorUnit > 0 || locatorZone >= 0);
        }
    }

    /** Existing external-host entry keeps its verified Provider boundary. */
    public FollowEngine(Context host) {
        this(host, false);
    }

    /**
     * Used only by our application-owned runtime, never selected by a model name or Intent extra.
     */
    FollowEngine(Context host, boolean localState) {
        this.host = host;
        this.localState = localState;
    }

    private Bundle exchange(String method, String arg, Bundle extras) {
        return localState
                ? RuntimeState.call(method, arg, extras)
                : host.getContentResolver().call(STATE, method, arg, extras);
    }

    boolean hasWorker() {
        Thread current = worker;
        return current != null && current.isAlive();
    }

    /** Read identity and length without enabling follow or changing lamp power. */
    public synchronized void identifyOutput() throws Exception {
        if (hasWorker() || !LightConnections.present(host) || !LightConnections.accessReady(host))
            return;
        Bundle settings = exchange("settings", null, null);
        JSONObject json = new JSONObject(settings.getString("json"));
        if (!json.isNull("layout")) return;
        OutputResolver.Connection connection = LightConnections.open(host);
        try {
            Bundle result = new Bundle();
            result.putString("module", connection.candidate.moduleId);
            result.putInt("units", connection.output.unitCount());
            Bundle resolved = exchange("resolve_output", json.getString("connection_key"), result);
            options = new Options(new JSONObject(resolved.getString("json")));
            lightModuleId = connection.candidate.moduleId;
            endpointId = connection.candidate.endpointId;
            lightName = connection.candidate.name;
            lightModel = connection.identity.model;
            lightUnits = connection.output.unitCount();
            lightReady = true;
            progress.connected();
        } finally {
            connection.output.close();
        }
    }

    public synchronized void configure() throws Exception {
        Bundle value = exchange("settings", configurationRevision, null);
        if (value == null) throw new IllegalStateException("Configuration unavailable");
        if (!value.containsKey("json")) return;
        Options next = new Options(new JSONObject(value.getString("json")));
        ConfigurationChange.Kind change =
                ConfigurationChange.between(
                        options == null ? null : options.sessionKey,
                        options == null ? null : options.frameKey,
                        next.sessionKey,
                        next.frameKey,
                        running);
        if (change == ConfigurationChange.Kind.SAME) {
            configurationRevision = value.getString("revision");
            return;
        }
        boolean restart = change == ConfigurationChange.Kind.RESTART;
        if (restart && !stop(15000))
            throw new IllegalStateException("Previous device session did not release");
        if (options != null && !options.sessionKey.equals(next.sessionKey) && progress.blocked())
            progress.stop();
        options = next;
        configurationRevision = value.getString("revision");
        pacing.wake();
        if (restart) start();
    }

    public synchronized void start() throws Exception {
        if (worker != null && worker.isAlive()) return;
        if (options == null) configure();
        if (!progress.begin(SystemClock.elapsedRealtime())) return;
        running = true;
        lightReady = false;
        lightUnits = 0;
        previousOutputAt = 0;
        batches = 0;
        sweeps = 0;
        captureMs = 0;
        cycleMs = 0;
        pointCount = 0;
        outputIntervalMs = 0;
        outputWriteMs = 0;
        firstUpdateMs = 0;
        worker = new Thread(this::stream, "frame-luma-capture");
        worker.setDaemon(true);
        worker.start();
    }

    public synchronized boolean stop(long timeout) throws Exception {
        running = false;
        pacing.wake();
        lightReady = false;
        progress.stop();
        Thread current = worker;
        if (current != null) current.join(Math.max(1, Math.min(timeout, 15000)));
        return current == null || !current.isAlive();
    }

    public String status() {
        return snapshot().toString();
    }

    JSONObject snapshot() {
        JSONObject json = new JSONObject();
        Options current = options;
        try {
            json.put("engine_revision", REVISION);
            json.put("running", running && lightReady && progress.streaming());
            json.put("phase", progress.phase());
            json.put("light_ready", lightReady);
            json.put("retry_after_ms", progress.retryAfter(SystemClock.elapsedRealtime()));
            json.put("worker", worker != null && worker.isAlive());
            json.put("period_ms", current == null ? FramePacer.DEFAULT_PERIOD_MS : current.period);
            json.put("brightness_percent", current == null ? 25 : current.brightness);
            json.put("capture_elapsed_ms", captureMs);
            json.put("actual_period_ms", cycleMs);
            json.put("first_update_ms", firstUpdateMs);
            json.put("output_interval_ms", outputIntervalMs);
            json.put("output_write_ms", outputWriteMs);
            LightOutput physical = activeOutput;
            if (physical != null) {
                json.put("output_frames", physical.framesWritten());
                if (physical.outputIntervalMs() > 0) {
                    json.put("output_interval_ms", physical.outputIntervalMs());
                    json.put("output_write_ms", physical.outputWriteMs());
                }
            }
            json.put("progressive", progressive);
            json.put("capture_source", captureSource);
            json.put("sample_points", pointCount);
            json.put("batches", batches);
            json.put("sweeps", sweeps);
            if (current != null && current.layout != null)
                json.put("sample_regions", current.layout.counts.length);
            json.put("device_id", deviceId);
            json.put("device_name", deviceName);
            json.put("light_name", lightName);
            json.put("error", progress.error());
            json.put("requires_configuration", progress.blocked());
            json.put("light_model", lightModel);
            json.put("light_unit_count", lightUnits);
            json.put("light_module_id", lightModuleId);
            json.put("endpoint_id", endpointId);
            JSONArray colors = new JSONArray();
            synchronized (colorsLock) {
                for (int color : lastColors) colors.put(color);
            }
            json.put("colors", colors);
            if (current != null && current.layout != null) {
                json.put("layout", current.layoutJson);
                json.put("locator_unit", current.locating() ? current.locatorUnit : 0);
                json.put("locator_zone", current.locating() ? current.locatorZone : -1);
            }
        } catch (Exception ignored) {
        }
        return json;
    }

    private void stream() {
        PixelSource source = null;
        LightOutput output = null;
        try {
            Options opening = options;
            DisplayModule display = Devices.display(host);
            deviceName = display.displayName();
            deviceId = display.id();
            OutputResolver.Connection connection = LightConnections.open(host);
            output = connection.output;
            lightName = connection.candidate.name;
            lightModuleId = connection.candidate.moduleId;
            endpointId = connection.candidate.endpointId;
            lightUnits = output.unitCount();
            Bundle detected = new Bundle();
            detected.putString("module", lightModuleId);
            detected.putInt("units", output.unitCount());
            Bundle resolved = exchange("resolve_output", opening.connectionKey, detected);
            Options observedOptions = options,
                    resolvedOptions = new Options(new JSONObject(resolved.getString("json")));
            if (!running
                    || !observedOptions.connectionKey.equals(opening.connectionKey)
                    || !resolvedOptions.connectionKey.equals(opening.connectionKey)
                    || !OPTIONS_SLOT.compareAndSet(this, observedOptions, resolvedOptions))
                throw new IllegalStateException("Configuration changed while connecting; retrying");
            if (options.layout == null)
                throw new IllegalStateException("Controller layout is not identified");
            LightConnections.module(host, lightModuleId).validateLayout(options.layout.geometry());
            if (options.layout.units != output.unitCount())
                throw new IllegalStateException(
                        "Detected output and physical calibration length differ");
            source = display.open(host);
            lightModel = "";
            output.begin();
            activeOutput = output;
            LightIdentity lightIdentity = output.identity();
            if (lightIdentity != null) {
                lightModel = lightIdentity.model;
            }
            lightReady = lightIdentity != null;
            progress.connected();
            FrameDispatch dispatch = new FrameDispatch(output);
            CaptureCapabilities policy = display.capabilities();
            boolean pointBackend = policy.mode == CaptureCapabilities.Mode.POINTS;
            ZoneSampler sampler = new ZoneSampler();
            ZoneSampler.Active active = () -> running;
            int[][][] coordinates = null;
            LayoutOptions previousSampling = null;
            int failures = 0;
            while (running) {
                long observed = pacing.stamp();
                long started = SystemClock.elapsedRealtime();
                Options current = options;
                try {
                    if (current.layout.units != output.unitCount())
                        throw new Exception("灯带长度与布局配置不匹配");
                    dispatch.begin(current, started);
                    EdgeLayout layout = current.geometry;
                    progressive =
                            pointBackend
                                    && policy.regionsPerUpdate < current.layout.counts.length
                                    && !current.locating();
                    Bundle ui = exchange("ui_frame", null, null);
                    boolean ownVisible = ui != null && ui.getBoolean("visible", false);
                    int[] ownColors = ownVisible ? ui.getIntArray("colors") : null;
                    long uiAt = ui == null ? 0 : ui.getLong("at", 0),
                            now = SystemClock.elapsedRealtime();
                    boolean ownFresh =
                            ownColors != null
                                    && ownColors.length == current.layout.counts.length
                                    && uiAt <= now
                                    && now - uiAt <= ZoneFrameMailbox.MAX_AGE_MS
                                    && current.layoutKey.equals(ui.getString("layout"));
                    if (current.locating()) {
                        captureSource = "locator";
                        if (current.locatorUnit > 0)
                            layout.locateInto(current.locatorUnit, dispatch.units);
                        else layout.markZoneInto(current.locatorZone, dispatch.units);
                        long before = SystemClock.elapsedRealtime();
                        send(output, dispatch.units, Math.min(current.brightness, 25));
                        dispatch.writeMs = SystemClock.elapsedRealtime() - before;
                        pointCount = 0;
                    } else if (ownVisible) {
                        // Our visible Compose tree is an independent, expiring RAM source.
                        captureSource = "own_app";
                        progressive = false;
                        if (!ownFresh) {
                            try {
                                pacing.await(
                                        observed,
                                        FramePacer.remainingMs(
                                                current.period,
                                                SystemClock.elapsedRealtime() - started));
                            } catch (InterruptedException interrupted) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                            continue;
                        }
                        for (int color : ownColors)
                            if (color < 0 || color > 0xffffff)
                                throw new Exception("Invalid own UI color");
                        long before = SystemClock.elapsedRealtime();
                        layout.mapInto(ownColors, dispatch.units);
                        send(output, dispatch.units, current.brightness);
                        dispatch.writeMs = SystemClock.elapsedRealtime() - before;
                        rememberColors(ownColors);
                        pointCount = current.layout.counts.length;
                        firstUpdateMs = (int) (SystemClock.elapsedRealtime() - started);
                        if (now - previousPreviewAt >= PREVIEW_INTERVAL_MS) {
                            previousPreviewAt = now;
                            publishPreview();
                        }
                    } else {
                        captureSource = "device";
                        if (coordinates == null || previousSampling != current.layout) {
                            coordinates =
                                    current.layout
                                            .profile()
                                            .points(
                                                    source.width(),
                                                    source.height(),
                                                    policy.edgeDepthPercent);
                            previousSampling = current.layout;
                            sampler =
                                    new ZoneSampler(); // New geometry must seed all regions again.
                        }
                        boolean complete =
                                sampler.sample(
                                        source,
                                        coordinates,
                                        policy.regionsPerUpdate,
                                        progressive,
                                        active,
                                        dispatch);
                        if (!complete) break;
                        pointCount = current.layout.counts.length;
                    }
                    captureMs = (int) (SystemClock.elapsedRealtime() - started - dispatch.writeMs);
                    sweeps++;
                    failures = 0;
                } catch (Exception error) {
                    progress.failed(rootMessage(error));
                    Log.w("FrameLumaEngine", progress.error());
                    if (++failures >= 3) break;
                }
                try {
                    pacing.await(
                            observed,
                            FramePacer.remainingMs(
                                    current.period, SystemClock.elapsedRealtime() - started));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
                cycleMs = (int) (SystemClock.elapsedRealtime() - started);
            }
        } catch (Exception error) {
            progress.failed(
                    error instanceof UnsupportedLightException
                            ? error.getMessage()
                            : rootMessage(error),
                    error instanceof UnsupportedLightException);
            Log.e("FrameLumaEngine", progress.error());
        } finally {
            if (source != null)
                try {
                    source.close();
                } catch (RuntimeException error) {
                    Log.w("FrameLumaEngine", "Capture cleanup", error);
                }
            try {
                if (output != null)
                    try {
                        output.end();
                    } finally {
                        output.close();
                    }
            } finally {
                activeOutput = null;
                boolean requested = running;
                running = false;
                lightReady = false;
                progress.finish(SystemClock.elapsedRealtime(), requested);
            }
        }
    }

    /** One sink and physical buffer per session, used only by the capture owner thread. */
    private final class FrameDispatch implements ZoneSampler.Sink {
        private final LightOutput output;
        private final int[] units;
        private final boolean[] freshUnits;
        private Options current;
        private long started, writeMs;
        private boolean first;

        FrameDispatch(LightOutput output) {
            this.output = output;
            units = new int[output.unitCount()];
            freshUnits = new boolean[output.unitCount()];
        }

        void begin(Options options, long now) {
            current = options;
            started = now;
            writeMs = 0;
            first = true;
        }

        public void publish(int[] colors, int completedZones, boolean[] fresh) throws Exception {
            if (!running) return;
            long before = SystemClock.elapsedRealtime();
            current.geometry.mapInto(colors, units);
            for (int unit = 0; unit < freshUnits.length; unit++) {
                int zone = current.geometry.zoneAt(unit);
                freshUnits[unit] = zone < 0 || fresh[zone];
            }
            send(output, units, current.brightness, freshUnits);
            long after = SystemClock.elapsedRealtime();
            writeMs += after - before;
            if (first) {
                firstUpdateMs = (int) (after - started);
                first = false;
            }
            rememberColors(colors);
            if (current.ui && after - previousPreviewAt >= PREVIEW_INTERVAL_MS) {
                previousPreviewAt = after;
                publishPreview();
            }
        }
    }

    private void rememberColors(int[] colors) {
        synchronized (colorsLock) {
            if (lastColors.length != colors.length) lastColors = new int[colors.length];
            System.arraycopy(colors, 0, lastColors, 0, colors.length);
        }
    }

    private void send(LightOutput target, int[] units, int brightness) throws Exception {
        send(target, units, brightness, null);
    }

    private void send(LightOutput target, int[] units, int brightness, boolean[] fresh)
            throws Exception {
        long before = SystemClock.elapsedRealtime();
        target.send(units, ++batches, brightness, fresh);
        if (running) progress.frame();
        long now = SystemClock.elapsedRealtime();
        outputWriteMs = (int) (now - before);
        if (previousOutputAt != 0) outputIntervalMs = (int) (now - previousOutputAt);
        previousOutputAt = now;
    }

    private void publishPreview() {
        try {
            JSONObject value = new JSONObject();
            value.put("event", "frame_preview");
            value.put("ready", true);
            value.put("running", running);
            value.put("engine", snapshot());
            if (localState) AppCore.reportOwned(value);
            else {
                Bundle bundle = new Bundle();
                bundle.putString("json", value.toString());
                exchange("report", null, bundle);
            }
        } catch (Exception ignored) {
        }
    }

    private static String rootMessage(Throwable error) {
        for (int depth = 0; depth < 5 && error.getCause() != null; depth++)
            error = error.getCause();
        return error.toString();
    }
}

/** Pure session decision, shared by live configure and its regression tests. */
final class ConfigurationChange {
    enum Kind {
        SAME,
        LIVE,
        RESTART
    }

    static Kind between(
            String previousSession,
            String previousFrame,
            String nextSession,
            String nextFrame,
            boolean running) {
        if (nextSession == null || nextFrame == null)
            throw new IllegalArgumentException("Missing configuration");
        boolean sameSession = nextSession.equals(previousSession);
        if (sameSession && nextFrame.equals(previousFrame)) return Kind.SAME;
        return running && previousSession != null && !sameSession ? Kind.RESTART : Kind.LIVE;
    }
}

/** Session progress is separate from worker liveness. Retry state stays in RAM. */
final class FollowState {
    private String phase = "stopped", error = "";
    private int failures;
    private long retryAt;
    private boolean blocked;

    synchronized boolean begin(long now) {
        if (blocked || now < retryAt) return false;
        phase = "connecting";
        return true;
    }

    synchronized void connected() {
        phase = "connected";
    }

    synchronized void frame() {
        phase = "streaming";
        error = "";
        failures = 0;
        retryAt = 0;
        blocked = false;
    }

    synchronized void failed(String value) {
        failed(value, false);
    }

    synchronized void failed(String value, boolean permanent) {
        blocked = permanent;
        phase = permanent ? "unsupported" : "retrying";
        error = value;
    }

    synchronized void finish(long now, boolean requested) {
        if (requested && blocked) {
            phase = "unsupported";
            retryAt = 0;
        } else if (requested && !error.isEmpty()) {
            retryAt = now + Math.min(30000L, 2000L << Math.min(failures++, 4));
            phase = "retrying";
        } else stop();
    }

    synchronized void stop() {
        phase = "stopped";
        error = "";
        failures = 0;
        retryAt = 0;
        blocked = false;
    }

    synchronized boolean blocked() {
        return blocked;
    }

    synchronized boolean streaming() {
        return "streaming".equals(phase);
    }

    synchronized String phase() {
        return phase;
    }

    synchronized String error() {
        return error;
    }

    synchronized long retryAfter(long now) {
        return Math.max(0, retryAt - now);
    }
}
