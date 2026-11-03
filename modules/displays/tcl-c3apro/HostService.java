package frameluma.devices.tcl.model27c2as;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.util.Log;

import dalvik.system.DexClassLoader;

import frameluma.deviceapi.OwnerIdentity;

import org.json.JSONObject;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Stable vendor discovery entry; every active session runs from the currently installed APK. */
public final class HostService extends Service {
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static final class Entry {
        private static final AtomicReference<AutoCloseable> active = new AtomicReference<>();
        private static final AtomicBoolean starting = new AtomicBoolean();
        private static String source = "";

        public String getRequest(Context requestContext) {
            if (starting.compareAndSet(false, true)) {
                new Thread(
                                () -> {
                                    try {
                                        bootstrap();
                                    } catch (Exception error) {
                                        Log.e(
                                                "FrameLumaHost",
                                                "Host session initialization failed",
                                                error);
                                    } finally {
                                        starting.set(false);
                                    }
                                },
                                "frame-luma-bootstrap")
                        .start();
            }
            return "[]";
        }

        private static void bootstrap() throws Exception {
            Context host =
                    (Context)
                            Class.forName("android.app.ActivityThread")
                                    .getMethod("currentApplication")
                                    .invoke(null);
            synchronized (host.getApplicationContext()) {
                ApplicationInfo owner =
                        host.getPackageManager().getApplicationInfo(OwnerIdentity.PACKAGE, 0);
                if ((owner.flags & ApplicationInfo.FLAG_STOPPED) != 0) return;
                if (active.get() != null && source.equals(owner.sourceDir)) return;
                if (active.get() != null) active.get().close();
                if (alreadyAttached(host, owner.sourceDir)) return;
                ClassLoader loader = new InstalledCode(owner.sourceDir, host);
                Class<?> runtime = Class.forName("frameluma.runtime.HostRuntime", true, loader);
                runtime.getMethod("attach", Context.class, AtomicReference.class)
                        .invoke(null, host, active);
                source = owner.sourceDir;
            }
        }

        private static boolean alreadyAttached(Context host, String apk) {
            try {
                Bundle value =
                        host.getContentResolver()
                                .call(
                                        Uri.parse(
                                                "content://"
                                                        + OwnerIdentity.PACKAGE
                                                        + ".state/state"),
                                        "state",
                                        null,
                                        null);
                JSONObject state = new JSONObject(value.getString("json", "{}"));
                return state.optBoolean("ready")
                        && apk.equals(state.optString("host_apk"))
                        && state.optInt("host_pid") == Process.myPid();
            } catch (Exception error) {
                return false;
            }
        }

        public void onResponse(Context context, String response) {}
    }

    private static final class InstalledCode extends DexClassLoader {
        InstalledCode(String apk, Context host) {
            super(apk, host.getCodeCacheDir().getAbsolutePath(), null, host.getClassLoader());
        }

        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith("frameluma.")) return super.loadClass(name, resolve);
            synchronized (this) {
                Class<?> type = findLoadedClass(name);
                if (type == null) type = findClass(name);
                if (resolve) resolveClass(type);
                return type;
            }
        }
    }
}
