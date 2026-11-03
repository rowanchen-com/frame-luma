package frameluma.deviceapi;

import android.app.PendingIntent;
import android.content.Context;

import frameluma.core.EdgeLayout;
import frameluma.core.LightCapabilities;
import frameluma.core.LightOutput;

import java.util.List;

public interface LightModule extends DeviceModule {
    TransportModule transport();

    default List<LightEndpoint> discover(Context context) {
        return transport().discover(context, config().section("transport"));
    }

    LightOutput open(Context context, LightEndpoint endpoint) throws Exception;

    LightCapabilities capabilities();

    void validateLayout(EdgeLayout layout) throws Exception;

    default boolean hasAccess(Context context, String endpointId) {
        return transport().hasAccess(context, endpointId);
    }

    default int foregroundServiceType() {
        return transport().foregroundServiceType();
    }

    default void requestAccess(Context context, String endpointId, PendingIntent result) {
        transport().requestAccess(context, endpointId, result);
    }
}
