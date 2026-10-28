package com.xiaomi.mitv.light.core;

/** One owner thread; borrowed color buffer is consumed synchronously by the sink. */
public final class ZoneSampler {
    // Spread each update around the perimeter; these are region IDs, not remapped colors.
    private static final int[] ORDER = {0, 3, 6, 1, 4, 7, 2, 5, 8};
    public interface Active { boolean get(); }
    public interface Sink { void publish(int[] colors, int completedZones) throws Exception; }
    private final int[] colors = new int[9];
    private boolean initialized;

    public boolean sample(PixelSource source, int[][][] points, boolean progressive,
                          Active active, Sink sink) throws Exception {
        source.beginFrame();
        for (int index = 0; index < 9; index++) {
            int zone = ORDER[index];
            int red = 0, green = 0, blue = 0;
            for (int[] xy : points[zone]) {
                if (!active.get()) return false;
                int rgb = source.readRgb(xy[0], xy[1]);
                red += (rgb >>> 16) & 255;
                green += (rgb >>> 8) & 255;
                blue += rgb & 255;
            }
            int count = points[zone].length;
            colors[zone] = (Math.round(red / (float) count) << 16)
                    | (Math.round(green / (float) count) << 8)
                    | Math.round(blue / (float) count);
            if (!active.get()) return false;
            // Seed all regions before the first output. Later updates retain the last
            // measured colors of regions not yet visited; no zone copies another zone.
            if (progressive && initialized && (index + 1) % 3 == 0)
                sink.publish(colors, index + 1);
        }
        if (!active.get()) return false;
        if (!progressive || !initialized) sink.publish(colors, 9);
        initialized = true;
        return true;
    }
}
