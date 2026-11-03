package frameluma.core;

/** Model-selected rendering policy. It contains no Android or vendor implementation. */
public final class UiRenderingProfile {
    public enum VideoSurface {
        EXTERNAL,
        EMBEDDED
    }

    public static final UiRenderingProfile DEFAULT = new UiRenderingProfile(VideoSurface.EXTERNAL);
    public final VideoSurface videoSurface;

    private UiRenderingProfile(VideoSurface videoSurface) {
        this.videoSurface = videoSurface;
    }

    public UiRenderingProfile withVideoSurface(VideoSurface value) {
        java.util.Objects.requireNonNull(value);
        return value == videoSurface ? this : new UiRenderingProfile(value);
    }
}
