package frameluma.devices.xiaomi.freeguy;

import android.content.Context;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;

import frameluma.core.*;
import frameluma.core.PixelSource;
import frameluma.deviceapi.*;
import frameluma.deviceapi.DisplayControl;

/** System-model selection; the installed TvService supplies capability and coordinates. */
public final class Adapter implements DisplayModule {
    private final ModuleConfig config;
    private final CaptureCapabilities capture;
    private final Control control;

    public Adapter(ModuleConfig config) {
        this.config = config;
        capture = config.capture();
        control = new Control(config.section("interface"));
    }

    public ModuleConfig config() {
        return config;
    }

    public CaptureCapabilities capabilities() {
        return capture;
    }

    public UiRenderingProfile uiRendering(Context context) {
        return config.rendering();
    }

    public DisplayControl control() {
        return control;
    }

    public PixelSource open(Context context) throws Exception {
        return new PixelReader(config.section("interface"));
    }
}

/** Runs in FrameLuma's own foreground service, never in the official light application's UID. */
final class Control implements DisplayControl {
    private final ModuleConfig settings;

    Control(ModuleConfig settings) {
        this.settings = settings;
    }

    public Execution execution() {
        return Execution.APPLICATION;
    }

    public BackgroundMode backgroundMode() {
        return BackgroundMode.valueOf(
                settings.string("background_mode", "foreground_service")
                        .toUpperCase(java.util.Locale.ROOT));
    }

    public boolean available(Context context) {
        return PixelReader.available(settings);
    }

    public boolean acceptsCaller(Context context, int uid) {
        return uid == Process.myUid() && available(context);
    }

    public boolean ownsHostProcess(Context context) {
        return false;
    }

    public void hostLease(Context context, boolean enabled) {
        throw new UnsupportedOperationException("Application-owned lifecycle");
    }

    public void send(Context context, Command command) {
        throw new UnsupportedOperationException("Use the application runtime");
    }
}

/** Fixed read-only TvService transactions, bounded replies and one serialized caller. */
final class PixelReader implements PixelSource {
    private final IBinder service;
    private final ModuleConfig settings;
    private final int[] window = new int[4], report = new int[10];
    private volatile boolean closed;
    private int width, height;

    PixelReader(ModuleConfig settings) throws Exception {
        this.settings = settings;
        service = connect(settings);
        queryCapabilities();
    }

    static boolean available(ModuleConfig settings) {
        try {
            connect(settings);
            return true;
        } catch (Exception error) {
            return false;
        }
    }

    private static IBinder connect(ModuleConfig settings) throws Exception {
        IBinder service =
                (IBinder)
                        Class.forName("android.os.ServiceManager")
                                .getMethod("getService", String.class)
                                .invoke(null, settings.string("service"));
        if (service == null
                || !settings.string("descriptor").equals(service.getInterfaceDescriptor()))
            throw new IllegalStateException("Xiaomi TvService is unavailable");
        return service;
    }

    private void queryCapabilities() throws Exception {
        Parcel request = Parcel.obtain(), reply = Parcel.obtain();
        try {
            request.writeInterfaceToken(settings.string("descriptor"));
            if (!service.transact(
                    settings.section("transactions").integer("supported"), request, reply, 0))
                throw new IllegalStateException("Color capability query rejected");
            reply.readException();
            if (reply.readInt() != 1)
                throw new IllegalStateException("Color reporting is not supported");
        } finally {
            request.recycle();
            reply.recycle();
        }
        request = Parcel.obtain();
        reply = Parcel.obtain();
        try {
            request.writeInterfaceToken(settings.string("descriptor"));
            if (!service.transact(
                    settings.section("transactions").integer("resolution"), request, reply, 0))
                throw new IllegalStateException("Color coordinate query rejected");
            reply.readException();
            int[] size = parseResolution(reply.readString());
            width = size[0];
            height = size[1];
        } finally {
            request.recycle();
            reply.recycle();
        }
    }

    static int[] parseResolution(String value) {
        String[] size = value == null ? new String[0] : value.trim().split("\\s*[xX×*]\\s*");
        if (size.length != 2)
            throw new IllegalArgumentException("Invalid color coordinate response");
        int width = Integer.parseInt(size[0]), height = Integer.parseInt(size[1]);
        if (width < 2 || height < 2)
            throw new IllegalArgumentException("Invalid color coordinate size");
        return new int[] {width, height};
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public synchronized int readRgb(int x, int y) throws Exception {
        if (closed || x < 0 || y < 0 || x >= width() - 1 || y >= height() - 1)
            throw new IllegalArgumentException("Invalid color coordinate");
        window[0] = x;
        window[1] = x + 1;
        window[2] = y;
        window[3] = y + 1;
        Parcel request = Parcel.obtain(), reply = Parcel.obtain();
        try {
            request.writeInterfaceToken(settings.string("descriptor"));
            request.writeInt(1);
            request.writeInt(1);
            request.writeIntArray(window);
            if (!service.transact(
                    settings.section("transactions").integer("colors"), request, reply, 0))
                throw new IllegalStateException("Color request rejected");
            reply.readException();
            if (reply.readInt() != 1 || reply.readInt() != 1)
                throw new IllegalStateException("No valid color returned");
            reply.readIntArray(report);
            if (reply.dataAvail() != 0)
                throw new IllegalStateException("Unexpected color reply data");
            return PixelReport.rgb(report);
        } finally {
            request.recycle();
            reply.recycle();
        }
    }

    public void close() {
        closed = true;
    }
}

/** Validates the documented ten-word ColorTaker reply before exposing an RGB value. */
final class PixelReport {
    static int rgb(int[] report) {
        if (report == null || report.length != 10)
            throw new IllegalArgumentException("Invalid color report shape");
        for (int i = 1; i < report.length; i++)
            if (report[i] < 0 || report[i] > 255)
                throw new IllegalArgumentException("Invalid color report channel");
        int rgb = (report[1] << 16) | (report[2] << 8) | report[3];
        if ((report[0] & 0xffffff) != rgb)
            throw new IllegalArgumentException("Inconsistent color report");
        return rgb;
    }
}
