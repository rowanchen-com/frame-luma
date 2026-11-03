package frameluma.runtime;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import frameluma.deviceapi.DisplayControl;
import frameluma.deviceapi.OwnerIdentity;

import org.json.JSONObject;

import java.security.MessageDigest;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** A complete replaceable host session, not an engine cached inside an old adapter runtime. */
public final class HostRuntime implements AutoCloseable {
    private final Context host;
    private final DisplayControl control;
    private final AtomicReference<AutoCloseable> slot;
    private final String apk;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor queue =
            new ThreadPoolExecutor(
                    0,
                    1,
                    15,
                    TimeUnit.SECONDS,
                    new ArrayBlockingQueue<Runnable>(32),
                    task -> {
                        Thread t = new Thread(task, "frame-luma-host");
                        t.setDaemon(true);
                        return t;
                    });
    private BroadcastReceiver commands, packages;
    private volatile long heartbeat;
    private volatile boolean closed, watching, updating;
    private boolean leased;
    private FollowEngine engine;
    private String lastError = "";

    public static void attach(Context host, AtomicReference<AutoCloseable> slot) throws Exception {
        synchronized (slot) {
            if (slot.get() != null) return;
            DisplayControl control = Devices.control(host);
            if (!control.ownsHostProcess(host))
                throw new SecurityException("Unverified host process");
            HostRuntime session = new HostRuntime(host, control, slot);
            try {
                session.register();
                slot.set(session);
            } catch (Exception error) {
                session.close();
                throw error;
            }
        }
    }

    private HostRuntime(Context host, DisplayControl control, AtomicReference<AutoCloseable> slot)
            throws Exception {
        this.host = host;
        this.control = control;
        this.slot = slot;
        apk = verifyOwner(host).sourceDir;
    }

    private void register() throws Exception {
        commands =
                new BroadcastReceiver() {
                    public void onReceive(Context context, Intent intent) {
                        String action = intent.getAction();
                        if (action == null || !action.startsWith(AppCore.CONTROL_PREFIX)) return;
                        DisplayControl.Command command;
                        try {
                            command =
                                    DisplayControl.Command.valueOf(
                                            action.substring(AppCore.CONTROL_PREFIX.length()));
                        } catch (IllegalArgumentException error) {
                            return;
                        }
                        if (command == DisplayControl.Command.HEARTBEAT) {
                            heartbeat = SystemClock.elapsedRealtime();
                            return;
                        }
                        submit(() -> handle(command));
                    }
                };
        IntentFilter filter = new IntentFilter();
        for (DisplayControl.Command command : DisplayControl.Command.values())
            filter.addAction(AppCore.CONTROL_PREFIX + command.name());
        if (Build.VERSION.SDK_INT >= 33)
            host.registerReceiver(
                    commands, filter, AppCore.PERMISSION, null, Context.RECEIVER_EXPORTED);
        else host.registerReceiver(commands, filter, AppCore.PERMISSION, null);
        packages =
                new BroadcastReceiver() {
                    public void onReceive(Context context, Intent intent) {
                        if (intent.getData() == null
                                || !AppCore.PKG.equals(intent.getData().getSchemeSpecificPart()))
                            return;
                        String action = intent.getAction();
                        if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
                            if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                                updating = true;
                                submit(
                                        () -> {
                                            stopEngine();
                                            publish("updating");
                                        });
                            } else {
                                queue.getQueue().clear();
                                submit(
                                        () -> {
                                            powerDown();
                                            close();
                                        });
                            }
                            return;
                        }
                        submit(
                                () -> {
                                    if (Intent.ACTION_PACKAGE_REPLACED.equals(action)) {
                                        boolean restart = enabled();
                                        close();
                                        if (restart) requestOwnerService();
                                    } else {
                                        powerDown();
                                        close();
                                    }
                                });
                    }
                };
        IntentFilter changes = new IntentFilter();
        changes.addDataScheme("package");
        for (String action :
                new String[] {
                    Intent.ACTION_PACKAGE_REMOVED,
                    Intent.ACTION_PACKAGE_REPLACED,
                    Intent.ACTION_PACKAGE_RESTARTED,
                    Intent.ACTION_PACKAGE_DATA_CLEARED
                }) changes.addAction(action);
        if (Build.VERSION.SDK_INT >= 33)
            host.registerReceiver(packages, changes, null, null, Context.RECEIVER_EXPORTED);
        else host.registerReceiver(packages, changes);
        control.hostLease(host, true);
        leased = true;
        publish("ready");
    }

    private interface Task {
        void run() throws Exception;
    }

    private void submit(Task task) {
        if (closed) return;
        try {
            queue.execute(
                    () -> {
                        try {
                            task.run();
                        } catch (Exception error) {
                            lastError = message(error);
                            publish("error");
                        }
                    });
        } catch (RejectedExecutionException error) {
            Log.w("FrameLumaHost", "Host queue unavailable");
        }
    }

    private FollowEngine engine() {
        if (engine == null) engine = new FollowEngine(host);
        return engine;
    }

    private void handle(DisplayControl.Command command) throws Exception {
        if (closed || updating) return;
        lastError = "";
        switch (command) {
            case START:
                if (!enabled()) {
                    stopEngine();
                    publish("disabled");
                    return;
                }
                engine().configure();
                engine.start();
                heartbeat = SystemClock.elapsedRealtime();
                watching = true;
                main.removeCallbacks(health);
                main.postDelayed(health, 1000);
                publish("session");
                return;
            case STOP:
                powerDown();
                publish("stopped");
                return;
            case PAUSE:
                stopEngine();
                publish("paused");
                return;
            case CONFIG:
                if (engine != null) engine.configure();
                publish("configured");
                return;
            case STATUS:
                engine().identifyOutput();
                publish("status");
                return;
            default:
                return;
        }
    }

    private final Runnable health =
            new Runnable() {
                public void run() {
                    if (closed || !watching) return;
                    submit(
                            () -> {
                                if (updating) return;
                                ApplicationInfo owner;
                                try {
                                    owner =
                                            host.getPackageManager()
                                                    .getApplicationInfo(AppCore.PKG, 0);
                                } catch (PackageManager.NameNotFoundException error) {
                                    powerDown();
                                    close();
                                    return;
                                }
                                if ((owner.flags & ApplicationInfo.FLAG_STOPPED) != 0
                                        || SystemClock.elapsedRealtime() - heartbeat > 7000) {
                                    powerDown();
                                    close();
                                } else if (engine != null && !engine.hasWorker()) {
                                    watching = false;
                                    publish("idle");
                                } else publish("status");
                            });
                    if (!closed && watching) main.postDelayed(this, 1000);
                }
            };

    private boolean enabled() {
        Bundle value = host.getContentResolver().call(AppCore.STATE_URI, "flow", null, null);
        return value != null && value.getBoolean("enabled", false);
    }

    private static ApplicationInfo verifyOwner(Context host) throws Exception {
        PackageInfo owner =
                host.getPackageManager()
                        .getPackageInfo(AppCore.PKG, PackageManager.GET_SIGNING_CERTIFICATES);
        Signature[] signatures =
                owner.signingInfo == null ? null : owner.signingInfo.getApkContentsSigners();
        if (signatures == null || signatures.length != 1)
            throw new SecurityException("Unexpected owner signer");
        StringBuilder hash = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray()))
            hash.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        if (!OwnerIdentity.CERTIFICATE.equals(hash.toString()))
            throw new SecurityException("Owner signature mismatch");
        return host.getPackageManager().getApplicationInfo(AppCore.PKG, 0);
    }

    private void stopEngine() throws Exception {
        watching = false;
        main.removeCallbacks(health);
        if (engine != null && !engine.stop(15000))
            throw new IllegalStateException("Engine release timed out");
    }

    private void powerDown() throws Exception {
        stopEngine();
        if (engine != null) LightConnections.power(host, false);
    }

    private void requestOwnerService() {
        host.startForegroundService(
                new Intent(FollowService.ACTION_START_FLOW)
                        .setComponent(
                                new ComponentName(AppCore.PKG, FollowService.class.getName())));
    }

    private void publish(String event) {
        try {
            JSONObject state =
                    new JSONObject()
                            .put("event", event)
                            .put("ready", !closed && !updating)
                            .put("running", false)
                            .put("error", lastError)
                            .put("host_apk", apk)
                            .put("host_pid", Process.myPid());
            if (engine != null) {
                JSONObject value = engine.snapshot();
                state.put("engine", value).put("running", value.optBoolean("running"));
            }
            Bundle report = new Bundle();
            report.putString("json", state.toString());
            host.getContentResolver().call(AppCore.STATE_URI, "report", null, report);
        } catch (Exception ignored) {
        }
    }

    public synchronized void close() throws Exception {
        if (closed) return;
        stopEngine();
        closed = true;
        main.removeCallbacksAndMessages(null);
        for (BroadcastReceiver receiver : new BroadcastReceiver[] {commands, packages})
            if (receiver != null) {
                try {
                    host.unregisterReceiver(receiver);
                } catch (IllegalArgumentException ignored) {
                }
            }
        queue.getQueue().clear();
        queue.shutdown();
        engine = null;
        slot.compareAndSet(this, null);
        if (leased) {
            try {
                control.hostLease(host, false);
            } catch (Exception error) {
                Log.w("FrameLumaHost", "Host lease release failed", error);
            }
            leased = false;
        }
        publish("closed");
    }

    private static String message(Throwable error) {
        for (int count = 0; count < 5 && error.getCause() != null; count++)
            error = error.getCause();
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
}
