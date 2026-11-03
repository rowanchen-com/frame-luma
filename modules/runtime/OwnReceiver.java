package frameluma.runtime;

import android.content.*;

public final class OwnReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        AppCore.initialize(context);
        Recovery.request(context, "lifecycle event");
        if (LightConnections.ACCESS_RESULT.equals(intent.getAction())) {
            LightConnections.invalidate();
            try {
                if (!LightConnections.accessReady(context)) {
                    AppCore.report(
                            "{\"running\":false,\"requires_usb_permission\":true,\"error\":\"USB访问尚未授权\"}");
                    return;
                }
            } catch (Exception ignored) {
                return;
            }
            Client.send(context, "STATUS");
        }
        if (AppCore.enabled() && AppCore.power())
            try {
                Client.flow(context, true);
            } catch (IllegalStateException error) {
                // A newer OS can postpone background startup; leave configuration intact for the
                // next visible launch.
                try {
                    AppCore.report("{\"running\":false,\"error\":\"请打开应用恢复随影\"}");
                } catch (Exception ignored) {
                }
            }
    }
}
