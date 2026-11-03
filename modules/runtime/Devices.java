package frameluma.runtime;

import android.content.Context;
import android.os.SystemClock;

import frameluma.core.CaptureCapabilities;
import frameluma.core.UiRenderingProfile;
import frameluma.deviceapi.DeviceModule;
import frameluma.deviceapi.DisplayControl;
import frameluma.deviceapi.DisplayModule;

/** Read television information, load configurations, then select among available interfaces. */
public final class Devices {
    private static DeviceModule.Info information;
    private static DisplayModule[] modules;
    private static DisplayModule selected;
    private static long attemptedAt;

    private Devices() {}

    private static synchronized DisplayModule findDisplay(Context context) {
        if (selected != null) return selected;
        if (information == null) information = DeviceModule.Info.read();
        if (modules == null) modules = GeneratedDevices.displays(context);
        long now = SystemClock.elapsedRealtime();
        if (attemptedAt > 0 && now - attemptedAt < 1000) return null;
        attemptedAt = now;
        DisplayModule best = null;
        int score = Integer.MIN_VALUE;
        boolean tied = false;
        for (DisplayModule module : modules) {
            if (!module.control().available(context)) continue;
            int candidate = module.matchScore(information);
            if (best == null || candidate > score) {
                best = module;
                score = candidate;
                tied = false;
            } else if (candidate == score) tied = true;
        }
        if (tied) throw new IllegalStateException("多个可用显示配置的发现优先级相同");
        selected = best;
        return selected;
    }

    public static DisplayModule display(Context context) throws Exception {
        DisplayModule module = findDisplay(context);
        if (module == null) throw new IllegalStateException("尚未找到可用的显示接口");
        return module;
    }

    public static DisplayControl control(Context context) throws Exception {
        return display(context).control();
    }

    public static boolean acceptsHost(Context context, int uid) {
        try {
            return control(context).acceptsCaller(context, uid);
        } catch (Exception error) {
            return false;
        }
    }

    public static UiRenderingProfile uiRendering(Context context) throws Exception {
        DisplayModule module = findDisplay(context);
        return module == null ? UiRenderingProfile.DEFAULT : module.uiRendering(context);
    }

    public static CaptureCapabilities capturePolicy(Context context) {
        DisplayModule module = findDisplay(context);
        return module == null ? null : module.capabilities();
    }

    public static String configurationDisplay(Context context) {
        DisplayModule module = findDisplay(context);
        return module == null ? "auto" : module.id();
    }

    public static boolean lightPresent(Context context) {
        return LightConnections.present(context);
    }
}
