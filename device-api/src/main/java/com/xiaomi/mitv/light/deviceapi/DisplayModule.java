package com.xiaomi.mitv.light.deviceapi;
import android.content.Context;
import com.xiaomi.mitv.light.core.*;

public interface DisplayModule extends DeviceModule {
    DeviceIdentity inspect(Context context) throws Exception;
    boolean matches(DeviceIdentity identity);
    CaptureCapabilities capabilities();
    PixelSource open(Context context,DeviceIdentity verifiedIdentity) throws Exception;
}
