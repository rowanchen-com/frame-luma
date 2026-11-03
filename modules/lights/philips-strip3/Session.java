package frameluma.devices.philips.strip3;

import android.os.SystemClock;
import android.util.Log;

import frameluma.core.BytePort;
import frameluma.core.ColorTransition;
import frameluma.core.LightCapabilities;
import frameluma.core.LightIdentity;
import frameluma.core.LightOutput;
import frameluma.deviceapi.ModuleConfig;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

final class Session implements LightOutput {
    private static final String TAG = "FrameLumaStrip3";
    private Integer originalPower;
    private Ownership ownership;
    private final Ownership.Port ownershipPort =
            new Ownership.Port() {
                public void command(String command) throws Exception {
                    accept(command);
                }

                public int linkage() throws Exception {
                    return scalar("get_pc_linkage", 0, 1);
                }
            };
    private int length;
    private volatile long frames;
    private volatile boolean reading;
    private volatile String readFailure;
    private Thread reader;
    private LightIdentity controllerIdentity;
    private BytePort port;
    private final Frames.Encoder encoder;
    private final String expectedModel;
    private final int unitCm;
    private final int[] supportedLengths;
    private final FrameInterval interval;
    private final int transitionMs, idleIntervalMs, blackHoldMs, darkThreshold;
    private final Object outputMonitor = new Object();
    private volatile boolean writing;
    private volatile String writeFailure;
    private volatile int actualIntervalMs, actualWriteMs;
    private Thread writer;
    private ColorTransition transition;
    private int[] latestFrame;
    private int revision, consumedRevision, outputBrightness;

    Session(BytePort port, ModuleConfig config, LightCapabilities capabilities) throws Exception {
        this.port = port;
        ModuleConfig protocol = config.section("capabilities");
        expectedModel = config.section("detection").string("model");
        unitCm = protocol.integer("physical_unit_cm");
        supportedLengths = protocol.integers("supported_lengths_m");
        encoder = new Frames.Encoder(capabilities, protocol);
        interval = new FrameInterval(protocol.integer("minimum_frame_interval_ms"));
        transitionMs = protocol.integer("transition_ms", 0);
        idleIntervalMs = protocol.integer("idle_frame_interval_ms", 500);
        blackHoldMs = protocol.integer("black_hold_ms", 0);
        darkThreshold = protocol.integer("dark_threshold", 0);
        if (transitionMs < 0 || idleIntervalMs < 1)
            throw new IllegalArgumentException("Invalid output pacing");
        try {
            receive(200);
        } catch (RuntimeException error) {
            close();
            throw error;
        }
    }

    @Override
    public LightIdentity identify() throws Exception {
        if (controllerIdentity != null) return controllerIdentity;
        LightIdentity detected = Replies.identity(exchange("model"), expectedModel);
        length = Replies.length(exchange("get_length"), supportedLengths);
        controllerIdentity = detected;
        return detected;
    }

    @Override
    public void power(boolean value) throws Exception {
        identify();
        int current = scalar("get_power", 0, 1);
        if (current != (value ? 1 : 0)) accept("set_power " + (value ? 1 : 0));
    }

    public void begin() throws Exception {
        identify();
        originalPower = scalar("get_power", 0, 1);
        int linkage = scalar("get_pc_linkage", 0, 1);
        logState("before", originalPower, linkage);
        ownership = new Ownership(originalPower, linkage);
        ownership.acquire(ownershipPort);
        startReader();
        startWriter();
    }

    @Override
    public LightIdentity identity() {
        return controllerIdentity;
    }

    @Override
    public int unitCount() {
        return length * 100 / unitCm;
    }

    @Override
    public void send(int[] rgb, int sequence, int brightness) throws Exception {
        send(rgb, sequence, brightness, null);
    }

    @Override
    public void send(int[] rgb, int sequence, int brightness, boolean[] fresh) throws Exception {
        if (controllerIdentity == null) throw new Exception("Controller identity not verified");
        if (readFailure != null) throw new Exception("Strip frame rejected: " + readFailure);
        if (writeFailure != null) throw new Exception("Strip output failed: " + writeFailure);
        if (rgb.length != unitCount() || brightness < 0 || brightness > 100)
            throw new IllegalArgumentException("Invalid output frame");
        for (int color : rgb)
            if (color < 0 || color > 0xffffff) throw new IllegalArgumentException("Invalid RGB");
        synchronized (outputMonitor) {
            if (!writing) throw new IllegalStateException("Strip output is stopped");
            boolean changed = transition.target(rgb, SystemClock.elapsedRealtime(), fresh);
            System.arraycopy(rgb, 0, latestFrame, 0, rgb.length);
            if (changed || outputBrightness != brightness) revision++;
            outputBrightness = brightness;
            outputMonitor.notifyAll();
        }
    }

    public int outputIntervalMs() {
        return actualIntervalMs;
    }

    public int outputWriteMs() {
        return actualWriteMs;
    }

    public long framesWritten() {
        return frames;
    }

    private void startWriter() {
        transition = new ColorTransition(unitCount(), transitionMs, blackHoldMs, darkThreshold);
        latestFrame = new int[unitCount()];
        revision = 0;
        consumedRevision = 0;
        writeFailure = null;
        writing = true;
        writer =
                new Thread(
                        () -> {
                            int[] frame = new int[unitCount()];
                            long previous = 0;
                            int sequence = 0;
                            boolean finalFramePending = false;
                            try {
                                while (writing) {
                                    int brightness, observedRevision;
                                    boolean movingAtSample;
                                    synchronized (outputMonitor) {
                                        if (revision == 0) {
                                            outputMonitor.wait();
                                            continue;
                                        }
                                        long now = SystemClock.elapsedRealtime();
                                        long wait = interval.waitMs(now);
                                        boolean changed = revision != consumedRevision;
                                        boolean moving = transition.moving(now);
                                        if (!changed
                                                && !moving
                                                && !finalFramePending
                                                && now - previous < idleIntervalMs)
                                            wait =
                                                    Math.max(
                                                            wait,
                                                            Math.max(
                                                                    1,
                                                                    idleIntervalMs
                                                                            - (now - previous)));
                                        if (wait > 0) {
                                            outputMonitor.wait(wait);
                                            continue;
                                        }
                                        transition.render(now, frame);
                                        brightness = outputBrightness;
                                        observedRevision = revision;
                                        movingAtSample = moving;
                                    }
                                    if (!writing) break;
                                    if (readFailure != null)
                                        throw new IllegalStateException(readFailure);
                                    long before = SystemClock.elapsedRealtime();
                                    int size;
                                    try {
                                        size = encoder.encode(frame, ++sequence, brightness);
                                    } catch (IllegalArgumentException geometryChange) {
                                        // Moving a physical zone boundary can increase run count
                                        // while fading.
                                        // Prefer its complete new mapping over an over-sized
                                        // protocol packet.
                                        synchronized (outputMonitor) {
                                            transition.replace(
                                                    latestFrame, SystemClock.elapsedRealtime());
                                            transition.finish(frame);
                                            observedRevision = revision;
                                        }
                                        movingAtSample = false;
                                        size = encoder.encode(frame, sequence, brightness);
                                    }
                                    port.write(encoder.bytes(), size, 1000);
                                    long after = SystemClock.elapsedRealtime();
                                    interval.completed(after);
                                    actualWriteMs = (int) (after - before);
                                    if (previous != 0) actualIntervalMs = (int) (after - previous);
                                    previous = after;
                                    frames++;
                                    finalFramePending = movingAtSample;
                                    synchronized (outputMonitor) {
                                        consumedRevision = observedRevision;
                                    }
                                }
                            } catch (Exception failure) {
                                if (writing) writeFailure = failure.toString();
                            } finally {
                                writing = false;
                            }
                        },
                        "strip3-output");
        writer.setDaemon(true);
        writer.start();
    }

    private void stopWriter() {
        writing = false;
        synchronized (outputMonitor) {
            outputMonitor.notifyAll();
        }
        Thread current = writer;
        if (current == null || current == Thread.currentThread()) return;
        try {
            current.join(1200);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
        if (current.isAlive()) {
            if (port != null) port.close();
            throw new IllegalStateException("USB output did not stop");
        }
        writer = null;
    }

    public void end() {
        try {
            stopWriter();
            stopReader(); // One input owner: release ACKs are read synchronously below.
            if (ownership != null) ownership.release(ownershipPort);
            if (ownership != null)
                logState("after", scalar("get_power", 0, 1), scalar("get_pc_linkage", 0, 1));
        } catch (Throwable error) {
            logError("cleanup_error", error);
        } finally {
            close();
        }
    }

    private void startReader() {
        reading = true;
        readFailure = null;
        reader =
                new Thread(
                        () -> {
                            byte[] bytes = new byte[128];
                            Replies replies = new Replies();
                            try {
                                while (reading) {
                                    int count = port.read(bytes, 100);
                                    if (count > 0) {
                                        String failure = replies.accept(bytes, count);
                                        if (failure != null) {
                                            readFailure = failure;
                                            break;
                                        }
                                    }
                                }
                            } catch (RuntimeException error) {
                                if (reading) readFailure = error.toString();
                            }
                        },
                        "strip3-usb-replies");
        reader.setDaemon(true);
        reader.start();
    }

    private void stopReader() {
        reading = false;
        Thread current = reader;
        if (current == null) return;
        try {
            current.join(400);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
        if (current.isAlive()) {
            // Do not race a still-blocked input owner with synchronous release reads.
            if (port != null) {
                port.close();
                port = null;
            }
            throw new IllegalStateException("USB reply reader did not stop");
        }
        reader = null;
    }

    private void logState(String phase, int power, int linkage) {
        try {
            JSONObject state = new JSONObject();
            state.put("event", "strip_state");
            state.put("phase", phase);
            state.put("power", power);
            state.put("pc_linkage", linkage);
            state.put("frames_sent", frames);
            state.put("length", length);
            Log.i(TAG, state.toString());
        } catch (Exception ignored) {
        }
    }

    private void logError(String event, Throwable error) {
        try {
            JSONObject value = new JSONObject();
            value.put("event", event);
            value.put("error", error.toString());
            Log.i(TAG, value.toString());
        } catch (Exception ignored) {
        }
    }

    void write(String command) throws Exception {
        byte[] bytes = (command + "\r\n").getBytes(StandardCharsets.US_ASCII);
        if (bytes.length >= encoder.bytes().length) throw new Exception("Frame too long");
        port.write(bytes, bytes.length, 1000);
    }

    String receive(int duration) {
        StringBuilder response = new StringBuilder();
        byte[] bytes = new byte[128];
        long until = SystemClock.elapsedRealtime() + duration;
        while (SystemClock.elapsedRealtime() < until && response.length() < 2048) {
            int remaining = (int) Math.max(1, until - SystemClock.elapsedRealtime());
            int count = port.read(bytes, Math.min(remaining, 150));
            if (count > 0) response.append(new String(bytes, 0, count, StandardCharsets.US_ASCII));
        }
        return response.toString();
    }

    String exchange(String command) throws Exception {
        // Discard unsolicited/stale replies before starting the next transaction.
        receive(8);
        write(command);
        StringBuilder received = new StringBuilder();
        byte[] bytes = new byte[128];
        long until = SystemClock.elapsedRealtime() + 600;
        while (SystemClock.elapsedRealtime() < until && received.length() < 2048) {
            int remaining = (int) Math.max(1, until - SystemClock.elapsedRealtime());
            int count = port.read(bytes, Math.min(remaining, 150));
            if (count > 0) received.append(new String(bytes, 0, count, StandardCharsets.US_ASCII));
            if (Replies.complete(command, received.toString(), expectedModel)) break;
        }
        String response = received.toString();
        JSONObject entry = new JSONObject();
        entry.put("event", "exchange");
        entry.put("tx", command);
        entry.put("rx", response);
        Log.d(TAG, entry.toString());
        if (response.contains("err"))
            throw new Exception("Command rejected: " + command + ": " + response);
        return response;
    }

    void accept(String command) throws Exception {
        String reply = exchange(command);
        if (!reply.matches("(?s).*\\bok\\b.*")) throw new Exception("No ACK: " + command);
    }

    int scalar(String command, int low, int high) throws Exception {
        String response = exchange(command);
        for (String line : response.split("[\\r\\n]+")) {
            if (line.trim().matches("[0-9]+")) {
                int value = Integer.parseInt(line.trim());
                if (value >= low && value <= high) return value;
            }
        }
        throw new Exception("No scalar reply: " + command);
    }

    public void close() {
        stopWriter();
        stopReader();
        if (port != null) {
            port.close();
            port = null;
        }
    }
}

/** Link-mode lifecycle after controller identity and exclusive USB-interface claim are verified. */
final class Ownership {
    interface Port {
        void command(String command) throws Exception;

        int linkage() throws Exception;
    }

    private final int originalPower, originalLinkage;
    private boolean touchedLinkage, touchedPower, released;

    Ownership(int power, int linkage) {
        if ((power != 0 && power != 1) || (linkage != 0 && linkage != 1))
            throw new IllegalArgumentException("Invalid strip state");
        originalPower = power;
        originalLinkage = linkage;
    }

    void acquire(Port port) throws Exception {
        // This bit is controller mode, not an OS ownership lock. Sleep can retain it while
        // power becomes zero. The caller already holds the exclusively claimed USB interface.
        touchedLinkage = true;
        if (originalLinkage != 0) {
            port.command("set_pc_linkage 0");
            port.command("set_pc_available 0");
        }
        if (originalPower == 0) {
            touchedPower = true;
            port.command("set_power 1");
        }
        port.command("set_pc_available 1");
        port.command("set_pc_linkage 1");
        if (port.linkage() != 1) throw new Exception("Linkage did not enable");
    }

    void release(Port port) throws Exception {
        if (released) return;
        released = true;
        Exception failure = null;
        if (touchedLinkage) {
            try {
                port.command("set_pc_linkage 0");
            } catch (Exception error) {
                failure = error;
            }
            try {
                port.command("set_pc_available 0");
            } catch (Exception error) {
                if (failure == null) failure = error;
            }
        }
        if (touchedPower && originalPower == 0)
            try {
                port.command("set_power 0");
            } catch (Exception error) {
                if (failure == null) failure = error;
            }
        if (failure != null) throw failure;
    }
}
