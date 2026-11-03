package frameluma.transports.usbserial;

import android.content.Context;
import android.hardware.usb.*;

import frameluma.core.BytePort;

import org.json.JSONObject;

import java.util.Locale;

/**
 * CH340 byte transport. USB selection and serial parameters come from the adapter configuration.
 */
final class Port implements BytePort {
    private UsbDeviceConnection connection;
    private final int controlTimeout;
    private UsbInterface iface;
    private UsbEndpoint input, output;

    Port(Context context, String endpoint, JSONObject settings) throws Exception {
        controlTimeout = settings.getInt("control_timeout_ms");
        if (controlTimeout <= 0)
            throw new IllegalArgumentException("Control timeout must be positive");
        try {
            UsbManager manager = context.getSystemService(UsbManager.class);
            UsbDevice device = manager.getDeviceList().get(endpoint);
            if (device == null || !frameluma.transports.usb.Adapter.matches(device, settings))
                throw new Exception("USB bridge no longer available");
            int index = settings.getInt("interface");
            if (index < 0 || index >= device.getInterfaceCount())
                throw new IllegalArgumentException("Configured USB interface is unavailable");
            iface = device.getInterface(index);
            for (int i = 0; i < iface.getEndpointCount(); i++) {
                UsbEndpoint value = iface.getEndpoint(i);
                if (value.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK
                        && value.getAddress() == settings.getInt("input_endpoint")) input = value;
                if (value.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK
                        && value.getAddress() == settings.getInt("output_endpoint")) output = value;
            }
            if (input == null
                    || output == null
                    || input.getDirection() != UsbConstants.USB_DIR_IN
                    || output.getDirection() != UsbConstants.USB_DIR_OUT)
                throw new Exception("Unexpected endpoints");
            connection = manager.openDevice(device);
            if (connection == null || !connection.claimInterface(iface, false))
                throw new Exception("USB interface unavailable");
            byte[] bridge = new byte[2];
            if (connection.controlTransfer(0xc0, 0x5f, 0, 0, bridge, 2, controlTimeout) != 2
                    || bridge[1] != 0) throw new Exception("Unexpected bridge reply");
            int rate = settings.getInt("baud_rate"), line = lineControl(settings);
            control(0xa1, 0, 0);
            baud(rate);
            control(0x9a, 0x2518, line);
            control(0xa1, 0x501f, 0xd90a);
            baud(rate);
            control(0x9a, 0x2518, line);
            int signals =
                    (settings.getBoolean("dtr") ? 0x20 : 0)
                            | (settings.getBoolean("rts") ? 0x40 : 0);
            control(0xa4, ~signals & 0xffff, 0);
            String flow = settings.getString("flow_control").toLowerCase(Locale.ROOT);
            if (!flow.equals("none") && !flow.equals("rts_cts"))
                throw new IllegalArgumentException("CH340 flow control must be none or rts_cts");
            control(0x9a, 0x2727, flow.equals("rts_cts") ? 0x0101 : 0);
        } catch (Exception error) {
            close();
            throw error;
        }
    }

    private void control(int request, int value, int index) throws Exception {
        if (connection.controlTransfer(0x40, request, value, index, null, 0, controlTimeout) < 0)
            throw new Exception("CH340 setup failed");
    }

    private static int lineControl(JSONObject settings) throws Exception {
        int bits = settings.getInt("data_bits"), stops = settings.getInt("stop_bits");
        if (bits < 5 || bits > 8 || (stops != 1 && stops != 2))
            throw new IllegalArgumentException("CH340 supports 5–8 data bits and 1 or 2 stop bits");
        int line = 0xc0 | (bits - 5) | (stops == 2 ? 4 : 0);
        switch (settings.getString("parity").toLowerCase(Locale.ROOT)) {
            case "none":
                return line;
            case "odd":
                return line | 0x08;
            case "even":
                return line | 0x18;
            case "mark":
                return line | 0x28;
            case "space":
                return line | 0x38;
            default:
                throw new IllegalArgumentException("Unknown serial parity");
        }
    }

    /** CH34x clock/register arithmetic; see usb-serial-for-android and NOTICE. */
    private void baud(int rate) throws Exception {
        if (rate <= 0) throw new IllegalArgumentException("Baud rate must be positive");
        long register;
        int scale;
        switch (rate) {
            case 921600:
                register = 0xf300;
                scale = 7;
                break;
            case 307200:
                register = 0xd900;
                scale = 7;
                break;
            default:
                long ticks = 1532620800L / rate;
                scale = 3;
                while (ticks > 65520 && scale > 0) {
                    ticks /= 8;
                    scale--;
                }
                if (ticks < 1 || ticks > 65520)
                    throw new IllegalArgumentException(
                            "Baud rate cannot be represented by this CH340 clock");
                register = 65536 - ticks;
        }
        control(0x9a, 0x1312, (int) (register & 0xff00) | scale | 0x80);
        control(0x9a, 0x0f2c, (int) (register & 0xff));
    }

    public int read(byte[] bytes, int timeoutMs) {
        UsbDeviceConnection current = connection;
        return current == null ? -1 : current.bulkTransfer(input, bytes, bytes.length, timeoutMs);
    }

    public void write(byte[] bytes, int length, int timeoutMs) throws Exception {
        if (bytes == null || length < 1 || length > bytes.length)
            throw new IllegalArgumentException("Invalid byte count");
        UsbDeviceConnection current = connection;
        if (current == null || current.bulkTransfer(output, bytes, length, timeoutMs) != length)
            throw new Exception("Incomplete serial write");
    }

    public void close() {
        UsbDeviceConnection current = connection;
        connection = null;
        if (current != null) {
            if (iface != null) current.releaseInterface(iface);
            current.close();
        }
    }
}
