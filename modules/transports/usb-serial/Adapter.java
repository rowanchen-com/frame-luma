package frameluma.transports.usbserial;

import android.content.Context;

import frameluma.core.BytePort;
import frameluma.deviceapi.LightEndpoint;
import frameluma.deviceapi.ModuleConfig;

/** CH34x initialization stays in this transport, never in a lamp protocol. */
public final class Adapter extends frameluma.transports.usb.Adapter {
    public Adapter(ModuleConfig config) {
        super(config);
    }

    @Override
    public BytePort open(Context context, LightEndpoint endpoint, ModuleConfig settings)
            throws Exception {
        return new Port(context, endpoint.id, settings.json());
    }
}
