package frameluma.transports.usb;

import android.app.PendingIntent;
import android.content.Context;
import android.content.pm.ServiceInfo;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.SystemClock;

import frameluma.core.BytePort;
import frameluma.deviceapi.LightEndpoint;
import frameluma.deviceapi.ModuleConfig;
import frameluma.deviceapi.TransportModule;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** USB discovery and permissions, plus direct bulk I/O without UART initialization. */
public class Adapter implements TransportModule {
    private static final String[] SELECTORS = {
        "vendor_id", "product_id", "device_class", "device_subclass", "device_protocol"
    };
    private final ModuleConfig config;

    public Adapter(ModuleConfig config) {
        this.config = config;
    }

    public ModuleConfig config() {
        return config;
    }

    public List<LightEndpoint> discover(Context context, ModuleConfig settings) {
        List<LightEndpoint> found = new ArrayList<>();
        JSONObject selector = settings.json();
        requireSelector(selector);
        for (UsbDevice device :
                context.getSystemService(UsbManager.class).getDeviceList().values()) {
            if (matches(device, selector))
                found.add(
                        new LightEndpoint(
                                device.getDeviceName(), "USB · " + device.getDeviceName()));
        }
        return found;
    }

    /** Selectors describe a USB connection, independently of the controller's lamp protocol. */
    public static boolean matches(UsbDevice device, JSONObject selector) {
        requireSelector(selector);
        return matches(selector, "vendor_id", device.getVendorId())
                && matches(selector, "product_id", device.getProductId())
                && matches(selector, "device_class", device.getDeviceClass())
                && matches(selector, "device_subclass", device.getDeviceSubclass())
                && matches(selector, "device_protocol", device.getDeviceProtocol());
    }

    private static boolean matches(JSONObject selector, String key, int actual) {
        return !selector.has(key) || selector.optInt(key, -1) == actual;
    }

    /** Connection settings alone must never turn into an unrestricted USB scan. */
    private static void requireSelector(JSONObject selector) {
        boolean present = false;
        for (int index = 0; index < SELECTORS.length; index++) {
            String key = SELECTORS[index];
            if (!selector.has(key)) continue;
            Object value = selector.opt(key);
            int maximum = index < 2 ? 0xffff : 0xff;
            if (!(value instanceof Number))
                throw new IllegalArgumentException("USB selector must be an integer: " + key);
            double number = ((Number) value).doubleValue();
            if (!Double.isFinite(number)
                    || number < 0
                    || number > maximum
                    || number != Math.rint(number))
                throw new IllegalArgumentException("Invalid USB selector: " + key);
            present = true;
        }
        if (!present)
            throw new IllegalArgumentException(
                    "USB configuration requires an explicit device selector");
    }

    public BytePort open(Context context, LightEndpoint endpoint, ModuleConfig settings)
            throws Exception {
        return new Port(context, endpoint.id, settings);
    }

    public boolean hasAccess(Context context, String endpointId) {
        UsbManager usb = context.getSystemService(UsbManager.class);
        UsbDevice device = usb.getDeviceList().get(endpointId);
        return device != null && usb.hasPermission(device);
    }

    public void requestAccess(Context context, String endpointId, PendingIntent result) {
        UsbManager usb = context.getSystemService(UsbManager.class);
        UsbDevice device = usb.getDeviceList().get(endpointId);
        if (device == null) throw new IllegalStateException("USB controller disconnected");
        usb.requestPermission(device, result);
    }

    public int foregroundServiceType() {
        return ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE;
    }
}

/** Optional input and required output bulk endpoints; no baud rate or serial line commands. */
final class Port implements BytePort {
    private volatile UsbDeviceConnection connection;
    private UsbInterface iface;
    private UsbEndpoint input, output;

    Port(Context context, String endpoint, ModuleConfig settings) throws Exception {
        try {
            UsbManager usb = context.getSystemService(UsbManager.class);
            UsbDevice device = usb.getDeviceList().get(endpoint);
            if (device == null || !Adapter.matches(device, settings.json()))
                throw new IllegalStateException("Configured USB connection is unavailable");
            int index = settings.integer("interface");
            if (index < 0 || index >= device.getInterfaceCount())
                throw new IllegalArgumentException("Configured USB interface is unavailable");
            iface = device.getInterface(index);
            int inputAddress = settings.integer("input_endpoint", -1);
            int outputAddress = settings.integer("output_endpoint");
            for (int i = 0; i < iface.getEndpointCount(); i++) {
                UsbEndpoint value = iface.getEndpoint(i);
                if (value.getType() != UsbConstants.USB_ENDPOINT_XFER_BULK) continue;
                if (value.getAddress() == inputAddress
                        && value.getDirection() == UsbConstants.USB_DIR_IN) input = value;
                if (value.getAddress() == outputAddress
                        && value.getDirection() == UsbConstants.USB_DIR_OUT) output = value;
            }
            if (output == null || (inputAddress >= 0 && input == null))
                throw new IllegalArgumentException("Configured USB bulk endpoints are unavailable");
            connection = usb.openDevice(device);
            if (connection == null || !connection.claimInterface(iface, true))
                throw new IllegalStateException("USB interface could not be opened");
        } catch (Exception error) {
            close();
            throw error;
        }
    }

    public int read(byte[] bytes, int timeoutMs) {
        UsbDeviceConnection current = connection;
        return current == null || input == null
                ? -1
                : current.bulkTransfer(input, bytes, bytes.length, timeoutMs);
    }

    public void write(byte[] bytes, int length, int timeoutMs) throws Exception {
        if (bytes == null || length < 1 || length > bytes.length || timeoutMs <= 0)
            throw new IllegalArgumentException("Invalid USB write parameters");
        UsbDeviceConnection current = connection;
        if (current == null) throw new IllegalStateException("USB connection is closed");
        long deadline = SystemClock.elapsedRealtime() + timeoutMs;
        int offset = 0;
        while (offset < length) {
            long remaining = deadline - SystemClock.elapsedRealtime();
            if (remaining <= 0) throw new IllegalStateException("USB write timed out");
            int count =
                    current.bulkTransfer(output, bytes, offset, length - offset, (int) remaining);
            if (count <= 0) throw new IllegalStateException("Incomplete USB write");
            offset += count;
        }
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
