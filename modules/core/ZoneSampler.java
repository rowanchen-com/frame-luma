package frameluma.core;

/** One owner thread; borrowed color buffer is consumed synchronously by the sink. */
public final class ZoneSampler {
    // Spread each update around the perimeter; these are region IDs, not remapped colors.
    public interface Active {
        boolean get();
    }

    public interface Sink {
        void publish(int[] colors, int completedZones, boolean[] fresh) throws Exception;
    }

    private int[] colors = new int[0], order = new int[0];
    private boolean[] fresh = new boolean[0];
    private int previousUpdate;
    private boolean previousProgressive;
    private boolean initialized;

    public boolean sample(
            PixelSource source,
            int[][][] points,
            int regionsPerUpdate,
            boolean progressive,
            Active active,
            Sink sink)
            throws Exception {
        if (points == null || points.length < 1 || points.length > 64)
            throw new IllegalArgumentException("Invalid region count");
        if (regionsPerUpdate < 1 || regionsPerUpdate > 64)
            throw new IllegalArgumentException("Invalid model update size");
        if (colors.length != points.length
                || previousUpdate != regionsPerUpdate
                || progressive != previousProgressive) {
            colors = new int[points.length];
            fresh = new boolean[points.length];
            order = new int[points.length];
            initialized = false;
            previousUpdate = regionsPerUpdate;
            previousProgressive = progressive;
            int at = 0, stride = Math.min(regionsPerUpdate, points.length);
            for (int column = 0; column < stride; column++)
                for (int zone = column; zone < points.length; zone += stride) order[at++] = zone;
        }
        source.beginFrame();
        java.util.Arrays.fill(fresh, false);
        for (int index = 0; index < points.length; index++) {
            int zone = order[index];
            if (points[zone] == null || points[zone].length < 1)
                throw new IllegalArgumentException("Missing sample points");
            int red = 0, green = 0, blue = 0;
            for (int[] xy : points[zone]) {
                if (!active.get()) return false;
                int rgb = source.readRgb(xy[0], xy[1]);
                red += (rgb >>> 16) & 255;
                green += (rgb >>> 8) & 255;
                blue += rgb & 255;
            }
            int count = points[zone].length;
            colors[zone] =
                    (Math.round(red / (float) count) << 16)
                            | (Math.round(green / (float) count) << 8)
                            | Math.round(blue / (float) count);
            fresh[zone] = true;
            if (!active.get()) return false;
            // Seed all regions before the first output. Later updates retain the last
            // measured colors of regions not yet visited; no zone copies another zone.
            if (progressive
                    && initialized
                    && ((index + 1) % regionsPerUpdate == 0 || index + 1 == points.length)) {
                sink.publish(colors, index + 1, fresh);
                java.util.Arrays.fill(fresh, false);
            }
        }
        if (!active.get()) return false;
        if (!progressive || !initialized) sink.publish(colors, points.length, fresh);
        initialized = true;
        return true;
    }
}
