package frameluma.runtime;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

import frameluma.deviceapi.DisplayControl;
import frameluma.deviceapi.OwnerIdentity;

/** User-enabled system binding. Capture keeps the app UID and uses the selected display adapter. */
public final class BackgroundService extends AccessibilityService {
    private static volatile BackgroundService connected;
    private FollowSession session;

    @Override
    protected void onServiceConnected() {
        AppCore.initialize(this);
        try {
            if (Devices.control(this).backgroundMode()
                    != DisplayControl.BackgroundMode.ACCESSIBILITY) return;
            AccessibilityServiceInfo information = getServiceInfo();
            information.packageNames = new String[] {OwnerIdentity.PACKAGE};
            information.flags =
                    Recovery.keys(this)
                            ? AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
                            : 0;
            setServiceInfo(information);
            if (session == null) session = new FollowSession(this);
            connected = this;
            Recovery.request(this, "accessibility connected");
            AppCore.foregroundStatus(true);
            if (AppCore.enabled() && AppCore.power()) session.start();
        } catch (Exception error) {
            release();
            android.util.Log.w("FrameLumaBackground", "Background connection unavailable", error);
        }
    }

    public static boolean connected() {
        return connected != null;
    }

    static void refreshKeys() {
        BackgroundService service = connected;
        if (service == null) return;
        new android.os.Handler(android.os.Looper.getMainLooper())
                .post(
                        () -> {
                            if (connected != service) return;
                            AccessibilityServiceInfo information = service.getServiceInfo();
                            information.flags =
                                    Recovery.keys(service)
                                            ? AccessibilityServiceInfo
                                                    .FLAG_REQUEST_FILTER_KEY_EVENTS
                                            : 0;
                            service.setServiceInfo(information);
                        });
    }

    @Override
    protected boolean onKeyEvent(android.view.KeyEvent event) {
        return Recovery.key(this, event);
    }

    static boolean startFollowing(Context context) throws Exception {
        BackgroundService service = connected;
        FollowSession current = service == null ? null : service.session;
        if (current == null) {
            AppCore.foregroundStatus(false);
            AppCore.report(
                    new org.json.JSONObject()
                            .put("running", false)
                            .put("error", context.getString(R.string.background_service_required))
                            .toString());
            return false;
        }
        AppCore.foregroundStatus(true);
        current.start();
        return true;
    }

    static void stopFollowing() {
        BackgroundService service = connected;
        if (service != null && service.session != null) service.session.pause();
    }

    private void release() {
        if (connected == this) {
            connected = null;
            AppCore.foregroundStatus(false);
        }
        if (session != null) {
            session.close();
            session = null;
        }
        Recovery.request(this, "accessibility disconnected");
    }

    @Override
    public boolean onUnbind(Intent intent) {
        release();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        release();
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}
}
