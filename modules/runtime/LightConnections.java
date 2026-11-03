package frameluma.runtime;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.SystemClock;

import frameluma.core.*;
import frameluma.deviceapi.*;

import java.util.*;
import java.util.function.Supplier;

/** Detection is metadata-only; the engine alone opens a transport and verifies its controller. */
public final class LightConnections {
    public static final String ACCESS_RESULT = OwnerIdentity.PACKAGE + ".USB_ACCESS_RESULT";
    private static LightModule[] registered;

    private static synchronized LightModule[] modules(Context context) {
        if (registered == null) registered = GeneratedDevices.lights(context);
        return registered;
    }

    private static final SnapshotCache<List<OutputResolver.Candidate>> discovery =
            new SnapshotCache<>(2000);
    private static boolean watching;

    private LightConnections() {}

    public static synchronized void watch(Context context) {
        if (watching) return;
        BroadcastReceiver receiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context c, Intent i) {
                        invalidate();
                        AppCore.connectionChanged();
                        Client.send(c, "STATUS");
                    }
                };
        IntentFilter filter = new IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        if (Build.VERSION.SDK_INT >= 33)
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else context.registerReceiver(receiver, filter);
        watching = true;
    }

    public static void invalidate() {
        discovery.invalidate();
    }

    public static List<OutputResolver.Candidate> candidates(Context context) {
        Context app = context.getApplicationContext();
        return discovery.get(SystemClock.elapsedRealtime(), () -> scan(app));
    }

    private static List<OutputResolver.Candidate> scan(Context context) {
        List<OutputResolver.Candidate> result = new ArrayList<>();
        for (LightModule module : modules(context))
            for (LightEndpoint endpoint : module.discover(context))
                result.add(
                        new OutputResolver.Candidate(
                                module.id(),
                                endpoint.id,
                                module.displayName(),
                                module.capabilities(),
                                () -> module.open(context, endpoint)));
        return Collections.unmodifiableList(result);
    }

    public static String topologyKey(Context context) {
        Set<String> endpoints = new TreeSet<>();
        for (OutputResolver.Candidate candidate : candidates(context))
            endpoints.add(candidate.endpointId);
        return String.join("|", endpoints);
    }

    public static boolean present(Context context) {
        return !candidates(context).isEmpty();
    }

    public static OutputResolver.Connection open(Context context) throws Exception {
        return OutputResolver.connect(candidates(context));
    }

    public static LightModule module(Context context, String id) {
        for (LightModule module : modules(context)) if (module.id().equals(id)) return module;
        throw new IllegalArgumentException("Unknown lighting module");
    }

    private static OutputResolver.Candidate endpoint(Context context) {
        OutputResolver.Candidate found = null;
        for (OutputResolver.Candidate candidate : candidates(context)) {
            if (found != null && !found.endpointId.equals(candidate.endpointId))
                throw new IllegalStateException("检测到多个灯光控制盒，请保留需要使用的连接");
            found = candidate;
        }
        return found;
    }

    public static boolean accessReady(Context context) {
        OutputResolver.Candidate candidate = endpoint(context);
        return candidate == null
                || module(context, candidate.moduleId).hasAccess(context, candidate.endpointId);
    }

    public static int foregroundServiceType(Context context) {
        int type = 0;
        for (OutputResolver.Candidate candidate : candidates(context))
            type |= module(context, candidate.moduleId).foregroundServiceType();
        return type;
    }

    public static void requestAccess(Context context) {
        OutputResolver.Candidate candidate = endpoint(context);
        if (candidate == null) throw new IllegalStateException("请先连接控制盒");
        if (module(context, candidate.moduleId).hasAccess(context, candidate.endpointId)) return;
        Intent intent = new Intent(context, OwnReceiver.class).setAction(ACCESS_RESULT);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_ONE_SHOT;
        if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
        module(context, candidate.moduleId)
                .requestAccess(
                        context,
                        candidate.endpointId,
                        PendingIntent.getBroadcast(context, 91, intent, flags));
    }

    /** No default driver or length is fabricated before a controller identifies itself. */
    public static LightModule identifiedModule(Context context) {
        String id = AppCore.detectedLightModule();
        return id.isEmpty() ? null : module(context, id);
    }

    public static String localizedName(Context context, String id, String fallback) {
        for (LightModule module : modules(context))
            if (module.id().equals(id))
                return module.config()
                        .text(
                                "display_name",
                                context.getResources().getConfiguration().getLocales().get(0));
        return fallback;
    }

    public static void power(Context context, boolean on) throws Exception {
        OutputResolver.Connection connection = open(context);
        try {
            connection.output.power(on);
        } finally {
            connection.output.close();
        }
    }
}

/** Process-local metadata snapshot. Empty discoveries are cached too; failed scans are not. */
final class SnapshotCache<T> {
    private final long ttl;
    private T value;
    private long at;

    SnapshotCache(long ttl) {
        if (ttl < 1) throw new IllegalArgumentException("Invalid snapshot lifetime");
        this.ttl = ttl;
    }

    synchronized T get(long now, Supplier<T> scan) {
        if (value == null || now < at || now - at >= ttl) {
            T next = java.util.Objects.requireNonNull(scan.get());
            value = next;
            at = now;
        }
        return value;
    }

    synchronized void invalidate() {
        value = null;
    }
}
