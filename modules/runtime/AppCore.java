package frameluma.runtime;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONObject;

public final class AppCore {
    public static final String PKG = frameluma.deviceapi.OwnerIdentity.PACKAGE,
            PERMISSION = PKG + ".permission.CONTROL";
    public static final String CONTROL_PREFIX = PKG + ".control.";
    public static final Uri STATE_URI = Uri.parse("content://" + PKG + ".state/state");
    private static Context context;
    private static volatile String state = "{\"ready\":false,\"running\":false}";
    private static volatile long reportedAt;
    private static volatile Boolean backgroundServiceAllowed;
    private static int locatorUnit, locatorZone = -1;
    private static long locatorUntil;
    private static volatile boolean uiVisible;
    private static final frameluma.core.ZoneFrameMailbox uiFrames =
            new frameluma.core.ZoneFrameMailbox();
    private static volatile LayoutOptions cachedLayout;
    private static String detectedConnection = "", detectedLight = "";
    private static int detectedUnits;
    private static final String configurationEpoch =
            Long.toHexString(android.os.SystemClock.elapsedRealtimeNanos());
    private static final java.util.concurrent.atomic.AtomicLong configurationVersion =
            new java.util.concurrent.atomic.AtomicLong(1);
    private static final java.util.concurrent.CopyOnWriteArraySet<Runnable> listeners =
            new java.util.concurrent.CopyOnWriteArraySet<>();
    private static final SharedPreferences.OnSharedPreferenceChangeListener layoutChanges =
            (preferences, key) -> {
                if (key == null || key.startsWith("layout:")) {
                    invalidateLayout();
                    changed();
                }
            };

    public static synchronized void initialize(Context value) {
        if (context == null) {
            context = value.getApplicationContext();
            prefs().registerOnSharedPreferenceChangeListener(layoutChanges);
            LightConnections.watch(context);
            Recovery.initialize(context);
        }
    }

    public static String configurationToken() {
        return configurationEpoch + ":" + configurationVersion.get();
    }

    private static void configurationChanged() {
        configurationVersion.incrementAndGet();
        changed();
    }

    public static Context context() {
        return context;
    }

    private static SharedPreferences prefs() {
        return context.getSharedPreferences("frame_luma", Context.MODE_PRIVATE);
    }

    public static boolean enabled() {
        return prefs().getBoolean("enabled", false);
    }

    public static boolean power() {
        return prefs().getBoolean("power", true);
    }

    public static int brightness() {
        return prefs().getInt("brightness", 25);
    }

    public static int period() {
        return frameluma.core.FramePacer.speedPeriod(
                frameluma.core.FramePacer.speedIndex(
                        prefs().getInt("period", frameluma.core.FramePacer.DEFAULT_PERIOD_MS)));
    }

    public static void setEnabled(boolean value) {
        if (value == enabled() && (!value || power())) return;
        SharedPreferences.Editor e = prefs().edit().putBoolean("enabled", value);
        if (value) e.putBoolean("power", true);
        else clearLocator();
        e.apply();
        configurationChanged();
    }

    public static void setPower(boolean value) {
        if (value == power() && (value || !enabled())) return;
        SharedPreferences.Editor e = prefs().edit().putBoolean("power", value);
        if (!value) {
            e.putBoolean("enabled", false);
            clearLocator();
        }
        e.apply();
        configurationChanged();
    }

    public static void setBrightness(int value) {
        value = Math.max(0, Math.min(100, value));
        if (value != brightness()) {
            prefs().edit().putInt("brightness", value).apply();
            configurationChanged();
        }
    }

    public static void setPeriod(int value) {
        value = frameluma.core.FramePacer.speedPeriod(frameluma.core.FramePacer.speedIndex(value));
        if (value != period()) {
            prefs().edit().putInt("period", value).apply();
            configurationChanged();
        }
    }

    public static void setUiVisible(boolean value) {
        if (uiVisible == value) return;
        uiVisible = value;
        BackgroundService.refreshKeys();
        if (!value) uiFrames.clear();
        configurationChanged();
    }

    public static boolean uiVisible() {
        return uiVisible;
    }

    public static void publishUiFrame(int[] colors, String layout) {
        if (uiVisible) uiFrames.publish(colors, android.os.SystemClock.elapsedRealtime(), layout);
    }

    public static android.os.Bundle uiFrame() {
        android.os.Bundle result = new android.os.Bundle();
        result.putBoolean("visible", uiVisible);
        frameluma.core.ZoneFrameMailbox.Frame frame =
                uiVisible ? uiFrames.read(android.os.SystemClock.elapsedRealtime()) : null;
        if (frame != null) {
            result.putIntArray("colors", frame.colors);
            result.putString("layout", frame.layout);
            result.putLong("at", frame.at);
        }
        return result;
    }

    /** Current physical connections are detected, never chosen through stored UI settings. */
    public static String connectionKey() {
        return Devices.configurationDisplay(context) + ":" + LightConnections.topologyKey(context);
    }

    public static synchronized String detectedLightModule() {
        return context != null && detectedConnection.equals(connectionKey()) ? detectedLight : "";
    }

    public static synchronized int outputUnits() {
        if (!detectedConnection.equals(connectionKey()) || detectedLight.isEmpty()) return 0;
        frameluma.core.LightCapabilities capabilities =
                LightConnections.module(context, detectedLight).capabilities();
        capabilities.requireUnits(detectedUnits);
        return detectedUnits;
    }

    public static synchronized void connectionChanged() {
        detectedConnection = "";
        detectedLight = "";
        detectedUnits = 0;
        invalidateLayout();
        changed();
    }

    public static synchronized void resolveOutput(String expected, String moduleId, int units)
            throws Exception {
        if (!expected.equals(connectionKey()))
            throw new IllegalStateException("Connection changed while identifying the controller");
        frameluma.deviceapi.LightModule light = LightConnections.module(context, moduleId);
        light.capabilities().requireUnits(units);
        if (!detectedConnection.equals(expected)
                || !detectedLight.equals(moduleId)
                || detectedUnits != units) {
            detectedConnection = expected;
            detectedLight = moduleId;
            detectedUnits = units;
            invalidateLayout();
            clearLocator();
            changed();
        }
    }

    private static String layoutProfile() {
        return LightConnections.identifiedModule(context)
                .config()
                .string("layout_profile", frameluma.layouts.LayoutCatalog.DEFAULT);
    }

    private static String layoutKey() {
        StringBuilder key = new StringBuilder("layout:");
        for (String part :
                new String[] {
                    Devices.configurationDisplay(context),
                    LightConnections.identifiedModule(context).id(),
                    layoutProfile(),
                    "default:" + outputUnits() + ":"
                }) key.append(part.length()).append(':').append(part);
        return key.toString();
    }

    public static synchronized LayoutOptions layout() {
        try {
            if (outputUnits() == 0) return null;
            if (cachedLayout != null) return cachedLayout;
            String stored = prefs().getString(layoutKey(), null);
            cachedLayout =
                    stored == null
                            ? LayoutOptions.defaults(layoutProfile(), outputUnits())
                            : new LayoutOptions(new JSONObject(stored));
            return cachedLayout;
        } catch (Exception error) {
            throw new IllegalStateException("Invalid installation layout", error);
        }
    }

    public static synchronized void setLayout(JSONObject value) throws Exception {
        if (outputUnits() == 0) throw new IllegalStateException("请等待控制盒识别完成");
        LayoutOptions validated = new LayoutOptions(value);
        if (validated.units != outputUnits())
            throw new IllegalArgumentException("Hardware length changed; reopen calibration");
        LightConnections.identifiedModule(context).validateLayout(validated.geometry());
        String text = validated.json().toString();
        if (text.equals(layout().json().toString())) return;
        prefs().edit().putString(layoutKey(), text).apply();
        invalidateLayout();
        clearLocator();
        changed();
    }

    public static synchronized void resetLayout() {
        if (outputUnits() == 0) return;
        prefs().edit().remove(layoutKey()).apply();
        invalidateLayout();
        clearLocator();
        changed();
    }

    public static synchronized void invalidateLayout() {
        cachedLayout = null;
        uiFrames.clear();
        configurationVersion.incrementAndGet();
    }

    public static synchronized void locate(int unit, int zone, int ttl) {
        if (layout() == null) throw new IllegalStateException("请等待控制盒识别完成");
        if (unit < 0
                || unit > layout().units
                || zone < -1
                || zone >= layout().counts.length
                || (unit > 0 && zone >= 0)
                || ttl < 1000
                || ttl > 30000) throw new IllegalArgumentException("定位参数范围");
        locatorUnit = unit;
        locatorZone = zone;
        locatorUntil = unit > 0 || zone >= 0 ? android.os.SystemClock.elapsedRealtime() + ttl : 0;
        configurationChanged();
    }

    public static synchronized void clearLocator() {
        if (locatorUnit == 0 && locatorZone == -1 && locatorUntil == 0) return;
        locatorUnit = 0;
        locatorZone = -1;
        locatorUntil = 0;
        configurationChanged();
    }

    public static synchronized void expireLocator() {
        if (locatorUntil > 0 && android.os.SystemClock.elapsedRealtime() >= locatorUntil)
            clearLocator();
    }

    public static synchronized String settings() {
        JSONObject j = new JSONObject();
        try {
            j.put("connection_key", connectionKey());
            j.put("enabled", enabled());
            j.put("power", power());
            j.put("period_ms", period());
            j.put("brightness_percent", brightness());
            j.put("layout", layout() == null ? JSONObject.NULL : layout().json());
            j.put("ui_visible", uiVisible);
            if (android.os.SystemClock.elapsedRealtime() >= locatorUntil) clearLocator();
            j.put("locator_unit", locatorUnit);
            j.put("locator_zone", locatorZone);
            j.put("locator_until_elapsed", locatorUntil);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return j.toString();
    }

    public static String state() {
        return state;
    }

    public static long reportedAt() {
        return reportedAt;
    }

    public static synchronized void foregroundStatus(boolean allowed) {
        if (Boolean.valueOf(allowed).equals(backgroundServiceAllowed)) return;
        backgroundServiceAllowed = allowed;
        // Permission acknowledgement must not make an old engine sample look fresh.
        try {
            state = new JSONObject(state).put("background_service_allowed", allowed).toString();
        } catch (Exception ignored) {
        }
        changed();
    }

    public static synchronized void report(String value) throws Exception {
        if (value.length() > 8192) throw new Exception("status too long");
        reportOwned(new JSONObject(value));
    }

    /**
     * Takes ownership of a newly built internal status object; callers must not mutate it
     * afterward.
     */
    static synchronized void reportOwned(JSONObject status) throws Exception {
        if (backgroundServiceAllowed != null)
            status.put("background_service_allowed", backgroundServiceAllowed);
        String next = status.toString();
        if (next.length() > 8192) throw new Exception("status too long");
        state = next;
        reportedAt = android.os.SystemClock.elapsedRealtime();
        changed();
    }

    /**
     * Local notification only. A listener must enqueue work, not read mutable UI from the caller
     * thread.
     */
    public static void addStateListener(Runnable listener) {
        listeners.add(java.util.Objects.requireNonNull(listener));
    }

    public static void removeStateListener(Runnable listener) {
        listeners.remove(listener);
    }

    public static void changed() {
        for (Runnable listener : listeners) listener.run();
    }
}
