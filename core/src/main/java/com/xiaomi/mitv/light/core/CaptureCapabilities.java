package com.xiaomi.mitv.light.core;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CaptureCapabilities {
    public enum Mode { POINTS, FRAME }
    public final Mode mode;
    public final int width,height;
    public final Map<String,Verification> inputs;
    public CaptureCapabilities(Mode mode,int width,int height,Map<String,Verification> inputs) {
        this.mode=mode;this.width=width;this.height=height;
        this.inputs=Collections.unmodifiableMap(new LinkedHashMap<>(inputs));
    }
}
