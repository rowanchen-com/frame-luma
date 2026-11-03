package frameluma.devices.philips.strip3;

import android.content.Context;

import frameluma.core.BytePort;
import frameluma.core.EdgeLayout;
import frameluma.core.LightCapabilities;
import frameluma.core.LightOutput;
import frameluma.deviceapi.LightEndpoint;
import frameluma.deviceapi.LightModule;
import frameluma.deviceapi.ModuleConfig;
import frameluma.deviceapi.TransportModule;

/** The RGB-PC controller protocol; physical transport values come from this adapter's config. */
public final class Adapter implements LightModule {
    private final ModuleConfig config;
    private final TransportModule transport;
    private final LightCapabilities capabilities;

    public Adapter(ModuleConfig config, TransportModule transport) {
        this.config = config;
        this.transport = transport;
        ModuleConfig protocol = config.section("capabilities");
        int cm = protocol.integer("physical_unit_cm");
        int[] lengths = protocol.integers("supported_lengths_m");
        int[] units = new int[lengths.length];
        for (int index = 0; index < units.length; index++) {
            if (cm <= 0 || lengths[index] * 100 % cm != 0)
                throw new IllegalArgumentException("Non-integral lamp units");
            units[index] = lengths[index] * 100 / cm;
        }
        capabilities = new LightCapabilities(units);
    }

    public ModuleConfig config() {
        return config;
    }

    public TransportModule transport() {
        return transport;
    }

    public LightOutput open(Context context, LightEndpoint endpoint) throws Exception {
        BytePort port = transport.open(context, endpoint, config.section("transport"));
        try {
            return new Session(port, config, capabilities);
        } catch (Exception error) {
            port.close();
            throw error;
        }
    }

    public LightCapabilities capabilities() {
        return capabilities;
    }

    public void validateLayout(EdgeLayout layout) {
        capabilities.requireUnits(layout.unitCount());
        int[] colors = new int[layout.zoneCount()];
        for (int index = 0; index < colors.length; index++)
            colors[index] = (0x102030 + index * 0x010101) & 0xffffff;
        new Frames.Encoder(capabilities, config.section("capabilities"))
                .encode(layout.map(colors), 1, 25);
    }
}
