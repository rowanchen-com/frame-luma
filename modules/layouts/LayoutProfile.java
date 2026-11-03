package frameluma.layouts;

import java.util.Locale;

/** A screen region topology plus installation defaults; independent of brands and transport. */
public interface LayoutProfile {
    String id();

    String displayName(Locale locale);

    String[] labels(Locale locale);

    String installationHint(Locale locale);

    String directionLabel(boolean reverse, Locale locale);

    default String displayName() {
        return displayName(Locale.getDefault());
    }

    default String[] labels() {
        return labels(Locale.getDefault());
    }

    /** Normalized screen path; reverse uses the same vertices in the opposite order. */
    float[][] outline();

    int[] defaultCounts(int unitCount);

    int[][][] points(int width, int height, int depthPercent);

    interface Geometry {
        float[][] outline();

        int[] defaultCounts(int unitCount);

        int[][][] points(int width, int height, int depthPercent);
    }
}
