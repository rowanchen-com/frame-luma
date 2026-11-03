package frameluma.devices.tcl.model27c2as;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;
import android.os.SystemClock;

import dalvik.system.PathClassLoader;

import frameluma.core.*;
import frameluma.core.PixelSource;
import frameluma.deviceapi.DisplayControl;
import frameluma.deviceapi.DisplayModule;
import frameluma.deviceapi.ModuleConfig;
import frameluma.deviceapi.OwnerIdentity;

import org.json.JSONObject;

import java.lang.reflect.*;

/** C3APRO adapter; actual host and SDK availability determine whether capture can run. */
public final class Adapter implements DisplayModule {
    private final ModuleConfig config;
    private final CaptureCapabilities capture;
    private final Control control;

    public Adapter(ModuleConfig config) {
        this.config = config;
        capture = config.capture();
        control = new Control(config.section("interface"));
    }

    public ModuleConfig config() {
        return config;
    }

    public DisplayControl control() {
        return control;
    }

    public UiRenderingProfile uiRendering(Context context) {
        return config.rendering();
    }

    public CaptureCapabilities capabilities() {
        return capture;
    }

    public PixelSource open(Context context) throws Exception {
        return new PixelReader(context, config.section("interface"), capture);
    }
}

/** Model routing; installed host identity and real service calls determine availability. */
final class Control implements DisplayControl {
    private final ModuleConfig settings;

    Control(ModuleConfig settings) {
        this.settings = settings;
    }

    private long lastStartAttempt;

    private int hostUid(Context context) throws Exception {
        return context.getPackageManager()
                .getApplicationInfo(settings.string("host_package"), 0)
                .uid;
    }

    @Override
    public boolean available(Context context) {
        try {
            hostUid(context);
            return true;
        } catch (Exception error) {
            return false;
        }
    }

    @Override
    public boolean acceptsCaller(Context context, int uid) {
        try {
            return uid == hostUid(context);
        } catch (Exception error) {
            return false;
        }
    }

    @Override
    public void send(Context context, Command command) {
        if (command != Command.STOP && command != Command.PAUSE)
            ensureHost(context, command == Command.STATUS);
        context.sendBroadcast(
                new Intent(OwnerIdentity.PACKAGE + ".control." + command.name())
                        .setPackage(settings.string("host_package")));
    }

    private synchronized void ensureHost(Context context, boolean visibleRequest) {
        long now = SystemClock.elapsedRealtime();
        try {
            Bundle value =
                    context.getContentResolver()
                            .call(
                                    Uri.parse(
                                            "content://" + OwnerIdentity.PACKAGE + ".state/state"),
                                    "state",
                                    null,
                                    null);
            JSONObject state = new JSONObject(value.getString("json", "{}"));
            long age = now - value.getLong("at", 0);
            if (!visibleRequest
                    && age >= 0
                    && age < 6000
                    && (state.optBoolean("ready") || state.has("engine"))) return;
        } catch (Exception ignored) {
        }
        if (lastStartAttempt > 0 && now - lastStartAttempt < 5000) return;
        lastStartAttempt = now;
        // The explicit service expects a resume scene in onStartCommand; its START filter
        // is only a routing name and is rejected as an operation when the host is cold.
        Intent start =
                new Intent(settings.string("start_action"))
                        .setComponent(
                                new ComponentName(
                                        settings.string("host_package"),
                                        settings.string("host_service")));
        if (context.startService(start) == null)
            throw new IllegalStateException("TCL host service did not start");
    }

    @Override
    public boolean ownsHostProcess(Context context) {
        try {
            return settings.string("host_package").equals(context.getPackageName())
                    && Process.myUid() == hostUid(context);
        } catch (Exception error) {
            return false;
        }
    }

    @Override
    public void hostLease(Context context, boolean enabled) throws Exception {
        if (!ownsHostProcess(context)) throw new SecurityException("Unexpected host process");
        Intent intent =
                new Intent(settings.string("lease_action"))
                        .setComponent(
                                new ComponentName(
                                        settings.string("host_package"),
                                        settings.string("host_service")));
        intent.putExtra("isKeepAlive", enabled);
        if (context.startService(intent) == null)
            throw new IllegalStateException("Host lease unavailable");
    }
}

final class PixelReader implements PixelSource {
    private final CaptureCapabilities capture;
    private final Object service, callback;
    private final Method getter;
    private Field red, green, blue;
    private volatile boolean received, closed;
    private int status, color;

    PixelReader(Context host, ModuleConfig settings, CaptureCapabilities capture) throws Exception {
        this.capture = capture;
        if (!settings.string("host_package").equals(host.getPackageName())
                || Process.myUid() != host.getApplicationInfo().uid)
            throw new Exception("TCL host context required");
        ClassLoader loader = host.getClassLoader();
        Class<?> api;
        try {
            api = Class.forName(settings.string("factory_class"), true, loader);
        } catch (ClassNotFoundException missing) {
            loader = new PathClassLoader(settings.string("sdk_jar"), loader);
            api = Class.forName(settings.string("factory_class"), true, loader);
        }
        Class<?> cb = Class.forName(api.getName() + "$doGetSystemPixelInfoCallback", true, loader);
        service =
                api.getMethod("getService", String.class, boolean.class)
                        .invoke(null, settings.string("instance"), false);
        if (service == null) throw new Exception("Pixel service unavailable");
        getter =
                api.getMethod(
                        "doGetSystemPixelInfo", int.class, int.class, int.class, int.class, cb);
        callback =
                Proxy.newProxyInstance(
                        loader,
                        new Class<?>[] {cb},
                        new InvocationHandler() {
                            @Override
                            public Object invoke(Object proxy, Method method, Object[] args)
                                    throws Throwable {
                                if ("onValues".equals(method.getName())) {
                                    Object value = args[0];
                                    status = ((Number) args[1]).intValue();
                                    if (red == null) {
                                        red = value.getClass().getField("RCrSum");
                                        green = value.getClass().getField("GYSum");
                                        blue = value.getClass().getField("BCbSum");
                                    }
                                    long r = Integer.toUnsignedLong(red.getInt(value)),
                                            g = Integer.toUnsignedLong(green.getInt(value)),
                                            b = Integer.toUnsignedLong(blue.getInt(value));
                                    if (r > 255 || g > 255 || b > 255)
                                        throw new IllegalStateException(
                                                "Unexpected single-pixel result");
                                    color = ((int) r << 16) | ((int) g << 8) | (int) b;
                                    received = true;
                                    return null;
                                }
                                if ("hashCode".equals(method.getName()))
                                    return System.identityHashCode(proxy);
                                if ("equals".equals(method.getName()))
                                    return args != null && proxy == args[0];
                                return "FrameLumaPixelCallback";
                            }
                        });
    }

    @Override
    public int width() {
        return capture.width;
    }

    @Override
    public int height() {
        return capture.height;
    }

    @Override
    public synchronized int readRgb(int x, int y) throws Exception {
        if (closed || x < 0 || x >= width() || y < 0 || y >= height())
            throw new IllegalArgumentException("Invalid pixel request");
        received = false;
        status = -1;
        getter.invoke(service, x, y, x, y, callback);
        if (!received || status != 0) throw new Exception("Pixel read failed");
        return color;
    }

    @Override
    public void close() {
        closed = true;
    }
}
