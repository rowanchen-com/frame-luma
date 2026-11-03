package frameluma.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CaptureCapabilities {
    public enum Mode {
        POINTS,
        FRAME
    }

    public final Mode mode;
    public final int width, height;

    /** Fixed model policy, compiled from that model's capability descriptor. */
    public final int edgeDepthPercent, regionsPerUpdate;

    public final Map<String, Verification> inputs;

    public CaptureCapabilities(
            Mode mode,
            int width,
            int height,
            Map<String, Verification> inputs,
            int edgeDepthPercent,
            int regionsPerUpdate) {
        if (mode == null
                || width < 1
                || height < 1
                || edgeDepthPercent < 1
                || edgeDepthPercent > 30
                || regionsPerUpdate < 1
                || regionsPerUpdate > 64)
            throw new IllegalArgumentException("Invalid model capture policy");
        this.mode = mode;
        this.width = width;
        this.height = height;
        this.edgeDepthPercent = edgeDepthPercent;
        this.regionsPerUpdate = regionsPerUpdate;
        this.inputs = Collections.unmodifiableMap(new LinkedHashMap<>(inputs));
    }
}
