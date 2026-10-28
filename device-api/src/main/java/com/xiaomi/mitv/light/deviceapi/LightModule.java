package com.xiaomi.mitv.light.deviceapi;
import android.content.Context;
import com.xiaomi.mitv.light.core.LightOutput;
import com.xiaomi.mitv.light.core.EdgeLayout;

public interface LightModule extends DeviceModule {
    boolean present(Context context);
    LightOutput open(Context context) throws Exception;
    void power(Context context,boolean on) throws Exception;
    String defaultLayoutAsset();
    void validateLayout(EdgeLayout layout) throws Exception;
}
