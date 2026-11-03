package frameluma.runtime;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.hardware.usb.UsbManager;
import android.os.*;
import android.util.Log;

import frameluma.deviceapi.DisplayControl;

public final class FollowService extends Service {
    public static final String ACTION_START_FLOW = AppCore.PKG + ".action.START_FLOW";
    public static final String ACTION_STOP_FLOW = AppCore.PKG + ".action.STOP_FLOW";
    private boolean foreground;
    private FollowSession session;

    @Override
    public void onCreate() {
        super.onCreate();
        AppCore.initialize(this);
        session = new FollowSession(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int id) {
        if (intent != null && ACTION_STOP_FLOW.equals(intent.getAction())) {
            Client.send(this, "STOP");
            stopOwn();
            return START_NOT_STICKY;
        }
        if (!AppCore.enabled() || !AppCore.power()) {
            Client.send(this, "STOP");
            stopOwn();
            return START_NOT_STICKY;
        }
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(
                new NotificationChannel(
                        "frame_luma_follow",
                        getString(R.string.follow_channel),
                        NotificationManager.IMPORTANCE_LOW));
        Notification.Builder builder = new Notification.Builder(this, "frame_luma_follow");
        Notification notification =
                builder.setSmallIcon(android.R.drawable.ic_menu_view)
                        .setContentTitle(getString(R.string.follow_title))
                        .setContentText(getString(R.string.follow_text))
                        .setOngoing(true)
                        .build();
        try {
            int requested =
                    Build.VERSION.SDK_INT >= 34
                            ? ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                            : 0;
            if (Devices.control(this).execution() == DisplayControl.Execution.APPLICATION)
                requested = LightConnections.foregroundServiceType(this);
            if (Build.VERSION.SDK_INT >= 29) startForeground(31, notification, requested);
            else startForeground(31, notification);
            int actual = Build.VERSION.SDK_INT >= 29 ? getForegroundServiceType() : requested;
            foreground = requested == 0 || actual != 0;
            if (!foreground)
                throw new IllegalStateException("Foreground service elevation was rejected");
            AppCore.foregroundStatus(foreground);
            Log.i("FrameLumaService", "foreground requested=" + requested + " actual=" + actual);
        } catch (Exception error) {
            if (!BackgroundService.connected()) AppCore.foregroundStatus(false);
            try {
                AppCore.report(
                        new org.json.JSONObject()
                                .put("running", false)
                                .put("error", getString(R.string.foreground_unavailable))
                                .toString());
            } catch (Exception ignored) {
            }
            Log.w("FrameLumaService", "Foreground device service unavailable", error);
            stopOwn();
            return START_NOT_STICKY;
        }
        session.start();
        return START_STICKY;
    }

    private void stopOwn() {
        if (session != null) session.pause();
        if (foreground) {
            stopForeground(true);
            foreground = false;
        }
        stopSelf();
    }

    @Override
    public void onDestroy() {
        if (session != null) session.close();
        if (foreground) stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

/** Shared sampling lifecycle for foreground services and user-enabled system bindings. */
final class FollowSession implements AutoCloseable {
    private static volatile FollowSession owner;
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private volatile boolean active, closed;
    private final Runnable heartbeat =
            new Runnable() {
                public void run() {
                    if (!active || closed) return;
                    if (!AppCore.enabled() || !AppCore.power()) {
                        pause();
                        Client.send(context, "STOP");
                        return;
                    }
                    if (!Client.send(context, "HEARTBEAT")) {
                        pause();
                        Client.send(context, "PAUSE");
                        return;
                    }
                    PowerManager power = context.getSystemService(PowerManager.class);
                    boolean awake = power != null && power.isInteractive();
                    Client.send(
                            context, awake && Devices.lightPresent(context) ? "START" : "PAUSE");
                    if (active && !closed) handler.postDelayed(this, 2000);
                }
            };
    private final BroadcastReceiver lifecycle =
            new BroadcastReceiver() {
                public void onReceive(Context ignored, Intent intent) {
                    if (!active || closed) return;
                    String action = intent.getAction();
                    if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)
                            || UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action))
                        LightConnections.invalidate();
                    if (Intent.ACTION_SCREEN_OFF.equals(action)
                            || UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action))
                        Client.send(context, "PAUSE");
                    handler.removeCallbacks(heartbeat);
                    handler.post(heartbeat);
                }
            };

    FollowSession(Context context) {
        this.context = context;
        IntentFilter changes = new IntentFilter();
        changes.addAction(Intent.ACTION_SCREEN_ON);
        changes.addAction(Intent.ACTION_SCREEN_OFF);
        changes.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        changes.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        if (Build.VERSION.SDK_INT >= 33)
            context.registerReceiver(lifecycle, changes, Context.RECEIVER_NOT_EXPORTED);
        else context.registerReceiver(lifecycle, changes);
    }

    void start() {
        synchronized (FollowSession.class) {
            if (closed) return;
            if (owner != null && owner != this) owner.pause();
            owner = this;
            active = true;
        }
        handler.removeCallbacks(heartbeat);
        handler.post(heartbeat);
    }

    void pause() {
        active = false;
        handler.removeCallbacksAndMessages(null);
        synchronized (FollowSession.class) {
            if (owner == this) owner = null;
        }
    }

    public void close() {
        if (closed) return;
        boolean owned = owner == this;
        closed = true;
        pause();
        context.unregisterReceiver(lifecycle);
        if (owned) Client.send(context, "PAUSE");
    }
}
