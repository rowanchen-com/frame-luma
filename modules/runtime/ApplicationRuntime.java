package frameluma.runtime;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import frameluma.deviceapi.DisplayControl;

import org.json.JSONObject;

import java.util.concurrent.*;

/** An application-owned engine for platforms whose public service accepts the ordinary app UID. */
final class ApplicationRuntime {
    private static ApplicationRuntime current;
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor queue =
            new ThreadPoolExecutor(
                    0,
                    1,
                    15,
                    TimeUnit.SECONDS,
                    new ArrayBlockingQueue<>(32),
                    task -> {
                        Thread t = new Thread(task, "frame-luma-application");
                        t.setDaemon(true);
                        return t;
                    });
    private FollowEngine engine;
    private boolean watching;
    private String error = "";

    static synchronized void send(Context context, DisplayControl.Command command) {
        if (current == null) current = new ApplicationRuntime(context.getApplicationContext());
        if (command != DisplayControl.Command.HEARTBEAT)
            current.submit(() -> current.handle(command));
    }

    private ApplicationRuntime(Context context) {
        this.context = context;
    }

    private interface Task {
        void run() throws Exception;
    }

    private void submit(Task task) {
        try {
            queue.execute(
                    () -> {
                        try {
                            task.run();
                        } catch (Exception problem) {
                            error = problem.toString();
                            publish("error");
                        }
                    });
        } catch (RejectedExecutionException ignored) {
        }
    }

    private void handle(DisplayControl.Command command) throws Exception {
        error = "";
        switch (command) {
            case START:
                if (!AppCore.enabled() || !AppCore.power()) {
                    stop(false);
                    publish("disabled");
                    return;
                }
                if (!LightConnections.accessReady(context)) {
                    stop(false);
                    publishAccessRequired();
                    return;
                }
                if (engine == null) engine = new FollowEngine(context, true);
                if (watching && engine.hasWorker()) {
                    engine.configure();
                    return;
                }
                engine.configure();
                engine.start();
                watching = true;
                main.removeCallbacks(health);
                main.postDelayed(health, 1000);
                publish("session");
                return;
            case STOP:
                stop(true);
                publish("stopped");
                return;
            case PAUSE:
                stop(false);
                publish("paused");
                return;
            case CONFIG:
                if (engine != null) engine.configure();
                publish("configured");
                return;
            case STATUS:
                if (engine == null) engine = new FollowEngine(context, true);
                engine.identifyOutput();
                publish("status");
                return;
            default:
                return;
        }
    }

    private final Runnable health =
            new Runnable() {
                public void run() {
                    submit(
                            () -> {
                                if (!watching) return;
                                if (!AppCore.enabled() || !AppCore.power()) {
                                    stop(true);
                                    publish("disabled");
                                    return;
                                }
                                if (engine != null && !engine.hasWorker()) {
                                    watching = false;
                                    publish("idle");
                                    return;
                                }
                                // Active preview reports already carry health and timing.
                                // Background sessions still
                                // publish once per second; the five-second freshness contract
                                // remains unchanged.
                                if (android.os.SystemClock.elapsedRealtime() - AppCore.reportedAt()
                                        >= 1000) publish("status");
                                if (watching) main.postDelayed(this, 1000);
                            });
                }
            };

    private void stop(boolean powerOff) throws Exception {
        watching = false;
        main.removeCallbacks(health);
        if (engine != null && !engine.stop(15000))
            throw new IllegalStateException("Engine release timed out");
        if (powerOff
                && engine != null
                && LightConnections.present(context)
                && LightConnections.accessReady(context)) LightConnections.power(context, false);
    }

    private void publishAccessRequired() {
        try {
            AppCore.report(
                    "{\"ready\":false,\"running\":false,\"requires_usb_permission\":true,\"error\":\"请允许USB控制盒访问\"}");
        } catch (Exception ignored) {
        }
    }

    private void publish(String event) {
        try {
            JSONObject state =
                    new JSONObject()
                            .put("event", event)
                            .put("ready", true)
                            .put("running", false)
                            .put("error", error);
            if (AppCore.enabled() && !LightConnections.accessReady(context))
                state.put("requires_usb_permission", true).put("error", "请允许USB控制盒访问");
            if (engine != null) {
                JSONObject value = engine.snapshot();
                state.put("engine", value).put("running", value.optBoolean("running"));
            }
            AppCore.reportOwned(state);
        } catch (Exception ignored) {
        }
    }
}
