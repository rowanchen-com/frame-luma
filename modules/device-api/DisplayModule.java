package frameluma.deviceapi;

import android.content.Context;

import frameluma.core.*;

public interface DisplayModule extends DeviceModule {
    default int matchScore(DeviceModule.Info device) {
        return config().matchScore(device);
    }

    CaptureCapabilities capabilities();

    UiRenderingProfile uiRendering(Context context) throws Exception;

    DisplayControl control();

    PixelSource open(Context context) throws Exception;
}
