package frameluma.runtime;

import android.content.Context;
import android.content.Intent;

public final class Client {
    public static boolean send(Context context, String action) {
        frameluma.deviceapi.DisplayControl.Command command =
                frameluma.deviceapi.DisplayControl.Command.valueOf(action);
        try {
            frameluma.deviceapi.DisplayControl control = frameluma.runtime.Devices.control(context);
            if (control.execution() == frameluma.deviceapi.DisplayControl.Execution.APPLICATION)
                ApplicationRuntime.send(context, command);
            else control.send(context, command);
            return true;
        } catch (Exception error) {
            return false;
        }
    }

    public static void refresh(Context context) {
        send(context, "CONFIG");
    }

    public static void flow(Context context, boolean enabled) {
        flow(context, enabled, false);
    }

    public static void flow(Context context, boolean enabled, boolean requestAccess) {
        Intent intent = new Intent(context, FollowService.class);
        intent.setAction(
                enabled ? FollowService.ACTION_START_FLOW : FollowService.ACTION_STOP_FLOW);
        // Stopping must not require a background service start on Android 9+.
        if (!enabled) {
            send(context, "STOP");
            BackgroundService.stopFollowing();
            context.stopService(intent);
            Recovery.request(context, "lighting stopped");
            return;
        }
        try {
            if (Devices.control(context).execution()
                            == frameluma.deviceapi.DisplayControl.Execution.APPLICATION
                    && !LightConnections.present(context)) {
                AppCore.report("{\"running\":false,\"error\":\"请先连接灯光控制盒\"}");
                return;
            }
            if (Devices.control(context).execution()
                            == frameluma.deviceapi.DisplayControl.Execution.APPLICATION
                    && !LightConnections.accessReady(context)) {
                send(context, "PAUSE");
                AppCore.report(
                        "{\"running\":false,\"requires_usb_permission\":true,\"error\":\"请允许USB控制盒访问\"}");
                if (requestAccess) LightConnections.requestAccess(context);
                return;
            }
            if (Devices.control(context).backgroundMode()
                    == frameluma.deviceapi.DisplayControl.BackgroundMode.ACCESSIBILITY) {
                boolean bound = BackgroundService.startFollowing(context);
                Recovery.request(context, "lighting started");
                if (!bound && Recovery.option(context, "foreground"))
                    context.startForegroundService(intent);
                return;
            }
        } catch (Exception error) {
            try {
                AppCore.report("{\"running\":false,\"error\":\"请检查设备连接与访问授权\"}");
            } catch (Exception ignored) {
            }
            return;
        }
        context.startForegroundService(intent);
    }
}
