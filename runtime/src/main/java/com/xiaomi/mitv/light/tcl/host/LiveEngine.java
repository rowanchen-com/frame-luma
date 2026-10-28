package com.xiaomi.mitv.light.tcl.host;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import com.xiaomi.mitv.light.core.*;
import com.xiaomi.mitv.light.deviceapi.*;
import com.xiaomi.mitv.light.runtime.Devices;
import com.xiaomi.mitv.light.tcl.layout.LayoutOptions;
import org.json.JSONArray;
import org.json.JSONObject;

/** Stable host ABI. One capture/output owner, bounded status, no per-frame disk writes. */
public final class LiveEngine {
    private static final String REVISION = "frame-luma-5";
    private static final Uri STATE = Uri.parse("content://com.xiaomi.mitv.light.tcl.state/state");
    private final Context host;
    private volatile Thread worker;
    private volatile boolean running;
    private volatile Options options;
    private volatile int batches, sweeps, captureMs, cycleMs, pointCount;
    private volatile int outputIntervalMs, outputWriteMs, firstUpdateMs;
    private long previousOutputAt, previousPreviewAt;
    private volatile boolean progressive;
    private volatile String captureSource = "device";
    private volatile int[] lastColors = new int[9];
    private volatile String deviceName = "等待机型验证", deviceId = "", lightName = "", lastError = "";
    private volatile Integer projectId;

    private static final class Options {
        final LayoutOptions layout;
        final int period, brightness, locatorUnit, locatorZone;
        final long until;
        final boolean ui;
        final String layoutKey;
        Options(JSONObject json) throws Exception {
            layout = new LayoutOptions(json.getJSONObject("layout"));
            layoutKey = layout.json().toString();
            period = FramePacer.normalizeLegacyPeriod(json.getInt("period_ms"));
            brightness = json.getInt("brightness_percent");
            if (brightness < 1 || brightness > 100) throw new Exception("Brightness out of range");
            locatorUnit = json.optInt("locator_unit", 0);
            locatorZone = json.optInt("locator_zone", -1);
            until = json.optLong("locator_until_elapsed", 0);
            ui = json.optBoolean("ui_visible", false);
            if (locatorUnit < 0 || locatorUnit > layout.units || locatorZone < -1 || locatorZone > 8
                    || (locatorUnit > 0 && locatorZone >= 0)) throw new Exception("Invalid locator");
        }
        boolean locating() {
            return until > SystemClock.elapsedRealtime() && (locatorUnit > 0 || locatorZone >= 0);
        }
        boolean sameFrameOptions(Options other) {
            return other != null && period == other.period && brightness == other.brightness && ui == other.ui
                    && locatorUnit == other.locatorUnit && locatorZone == other.locatorZone
                    && until == other.until && layoutKey.equals(other.layoutKey);
        }
    }

    public LiveEngine(Context host) { this.host = host; }
    public String revision() { return REVISION; }
    public void configure(String ignored) throws Exception {
        Bundle value = host.getContentResolver().call(STATE, "settings", null, null);
        options = new Options(new JSONObject(value.getString("json")));
    }
    public synchronized void start() throws Exception {
        if (worker != null && worker.isAlive()) return;
        if (options == null) configure("{}");
        running = true; lastError = ""; previousOutputAt = 0;
        worker = new Thread(this::stream, "reloadable-led-engine");
        worker.setDaemon(true); worker.start();
    }
    public synchronized boolean stop(long timeout) throws Exception {
        running = false;
        Thread current = worker;
        if (current != null) current.join(Math.max(1, Math.min(timeout, 15000)));
        return current == null || !current.isAlive();
    }
    public String status() {
        JSONObject json = new JSONObject();
        try {
            json.put("engine_revision", REVISION); json.put("running", running);
            json.put("worker", worker != null && worker.isAlive());
            json.put("period_ms", options == null ? 150 : options.period);
            json.put("brightness_percent", options == null ? 25 : options.brightness);
            json.put("capture_elapsed_ms", captureMs); json.put("actual_period_ms", cycleMs);
            json.put("first_update_ms", firstUpdateMs); json.put("output_interval_ms", outputIntervalMs);
            json.put("output_write_ms", outputWriteMs); json.put("progressive", progressive);
            json.put("capture_source", captureSource);
            json.put("sample_points", pointCount); json.put("batches", batches); json.put("sweeps", sweeps);
            json.put("device_id", deviceId); json.put("device_name", deviceName);
            json.put("light_name", lightName); json.put("project_id", projectId); json.put("error", lastError);
            JSONArray colors = new JSONArray();
            for (int color : lastColors) colors.put(color);
            json.put("colors", colors);
            if (options != null) {
                json.put("layout", options.layout.json());
                json.put("locator_unit", options.locating() ? options.locatorUnit : 0);
                json.put("locator_zone", options.locating() ? options.locatorZone : -1);
            }
        } catch (Exception ignored) { }
        return json.toString();
    }

    private void stream() {
        PixelSource source = null;
        LightOutput output = null;
        try {
            DisplayModule display = Devices.display(host);
            DeviceIdentity identity = display.inspect(host);
            deviceName = display.displayName(); deviceId = display.id(); projectId = identity.projectId;
            LightModule light = Devices.light(host); lightName = light.displayName();
            source = display.open(host, identity); output = light.open(host); output.begin();
            final LightOutput target = output;
            boolean pointBackend = display.capabilities().mode == CaptureCapabilities.Mode.POINTS;
            ZoneSampler sampler = new ZoneSampler();
            ZoneSampler.Active active = () -> running;
            int[][][] coordinates = null;
            int previousSamples = 0, previousDepth = 0, failures = 0;
            while (running) {
                long started = SystemClock.elapsedRealtime();
                Options current = options;
                try {
                    if (current.layout.units != output.unitCount()) throw new Exception("灯带长度与布局配置不匹配");
                    EdgeLayout layout = current.layout.geometry();
                    final long[] outputTime = {0};
                    final boolean[] first = {true};
                    progressive = pointBackend && current.period <= 300 && !current.locating();
                    Bundle ui = host.getContentResolver().call(STATE, "ui_frame", null, null);
                    boolean ownVisible = ui != null && ui.getBoolean("visible", false);
                    int[] ownColors = ownVisible ? ui.getIntArray("colors") : null;
                    long uiAt = ui == null ? 0 : ui.getLong("at", 0), now = SystemClock.elapsedRealtime();
                    boolean ownFresh = ownColors != null && ownColors.length == 9 && uiAt <= now
                            && now - uiAt <= ZoneFrameMailbox.MAX_AGE_MS && current.layoutKey.equals(ui.getString("layout"));
                    if (current.locating()) {
                        captureSource = "locator";
                        send(output, current.locatorUnit > 0 ? layout.locate(current.locatorUnit)
                                : layout.markZone(current.locatorZone), Math.min(current.brightness, 25));
                        pointCount = 0;
                    } else if (ownVisible) {
                        // The hardware API reads the video plane, excluding our Android UI.
                        // Our own visible View tree supplies a separate, expiring RAM source.
                        captureSource = "own_app"; progressive = false;
                        if (!ownFresh) { SystemClock.sleep(25); continue; }
                        for (int color : ownColors) if (color < 0 || color > 0xffffff) throw new Exception("Invalid own UI color");
                        long before = SystemClock.elapsedRealtime();
                        send(output, layout.map(ownColors), current.brightness);
                        outputTime[0] = SystemClock.elapsedRealtime() - before;
                        lastColors = ownColors.clone(); pointCount = 9 * current.layout.samples;
                        firstUpdateMs = (int) (SystemClock.elapsedRealtime() - started);
                        if (now - previousPreviewAt >= 250) { previousPreviewAt = now; publishPreview(); }
                    } else {
                        captureSource = "device";
                        if (coordinates == null || previousSamples != current.layout.samples || previousDepth != current.layout.depth) {
                            coordinates = EdgeRegions.points(source.width(), source.height(), current.layout.samples, current.layout.depth);
                            previousSamples = current.layout.samples; previousDepth = current.layout.depth;
                            sampler = new ZoneSampler(); // New geometry must seed all regions again.
                        }
                        boolean complete = sampler.sample(source, coordinates, progressive, active, (colors, done) -> {
                            if (!running) return;
                            long before = SystemClock.elapsedRealtime();
                            send(target, layout.map(colors), current.brightness);
                            long after = SystemClock.elapsedRealtime();
                            outputTime[0] += after - before;
                            if (first[0]) { firstUpdateMs = (int) (after - started); first[0] = false; }
                            lastColors = colors.clone();
                            if (current.ui && after - previousPreviewAt >= 250) {
                                previousPreviewAt = after; publishPreview();
                            }
                        });
                        if (!complete) break;
                        pointCount = 9 * current.layout.samples;
                    }
                    captureMs = (int) (SystemClock.elapsedRealtime() - started - outputTime[0]);
                    sweeps++; failures = 0; lastError = "";
                } catch (Exception error) {
                    lastError = rootMessage(error); Log.w("FrameLumaEngine", lastError);
                    if (++failures >= 3) break;
                }
                long remaining = FramePacer.remainingMs(current.period, SystemClock.elapsedRealtime() - started);
                while (running && remaining > 0) {
                    long part = Math.min(remaining, 25); SystemClock.sleep(part); remaining -= part;
                    // Heartbeats reconstruct Options; unchanged settings must not shorten a preset.
                    if (!options.sameFrameOptions(current)) break;
                }
                cycleMs = (int) (SystemClock.elapsedRealtime() - started);
            }
        } catch (Exception error) {
            lastError = rootMessage(error); Log.e("FrameLumaEngine", lastError);
        } finally {
            if (source != null) try { source.close(); } catch (RuntimeException error) { Log.w("FrameLumaEngine", "Capture cleanup", error); }
            try { if (output != null) try { output.end(); } finally { output.close(); } }
            finally { running = false; }
        }
    }
    private void send(LightOutput target, int[] units, int brightness) throws Exception {
        long before = SystemClock.elapsedRealtime();
        target.send(units, ++batches, brightness);
        long now = SystemClock.elapsedRealtime();
        outputWriteMs = (int) (now - before);
        if (previousOutputAt != 0) outputIntervalMs = (int) (now - previousOutputAt);
        previousOutputAt = now;
    }
    private void publishPreview() {
        try {
            JSONObject value = new JSONObject(); value.put("event", "frame_preview");
            value.put("running", running); value.put("engine", new JSONObject(status()));
            Bundle bundle = new Bundle(); bundle.putString("json", value.toString());
            host.getContentResolver().call(STATE, "report", null, bundle);
        } catch (Exception ignored) { }
    }
    private static String rootMessage(Throwable error) {
        for (int depth = 0; depth < 5 && error.getCause() != null; depth++) error = error.getCause();
        return error.toString();
    }
}
