package frameluma.deviceapi;

import android.app.PendingIntent;
import android.content.Context;

import frameluma.core.BytePort;

import java.util.List;

/** Discovers and opens byte connections; knows neither television capture nor lamp commands. */
public interface TransportModule extends DeviceModule {
    List<LightEndpoint> discover(Context context, ModuleConfig settings);

    BytePort open(Context context, LightEndpoint endpoint, ModuleConfig settings) throws Exception;

    default boolean hasAccess(Context context, String endpointId) {
        return true;
    }

    default int foregroundServiceType() {
        return 0;
    }

    default void requestAccess(Context context, String endpointId, PendingIntent result) {
        throw new UnsupportedOperationException("This connection has no access prompt");
    }
}
