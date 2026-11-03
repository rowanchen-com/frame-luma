package frameluma.layouts;

/** Front view, bottom-centre towards the left. Region geometry is explicitly a layout policy. */
public final class FourEdgeNine implements LayoutProfile.Geometry {
    public float[][] outline() {
        return new float[][] {{.5f, 1}, {0, 1}, {0, 0}, {1, 0}, {1, 1}, {.5f, 1}};
    }

    public int[] defaultCounts(int units) {
        if (units < 1 || units > 4096) throw new IllegalArgumentException("Invalid output length");
        int[] weights = {3, 2, 2, 2, 2, 2, 2, 2, 3}, result = new int[weights.length];
        int used = 0;
        for (int i = 0; i < result.length; i++) {
            result[i] = units * weights[i] / 20;
            used += result[i];
        }
        while (used < units) {
            int best = 0;
            for (int i = 1; i < weights.length; i++)
                if (units * weights[i] - result[i] * 20 > units * weights[best] - result[best] * 20)
                    best = i;
            result[best]++;
            used++;
        }
        return result;
    }

    public int[][][] points(int width, int height, int depth) {
        return EdgeRegions.points(width, height, depth);
    }
}

/** Normalized regions translated into real panel coordinates by the selected backend. */
final class EdgeRegions {
    private EdgeRegions() {}

    public static int[][][] points(int width, int height, int depthPercent) {
        if (width < 16 || height < 16 || depthPercent < 1 || depthPercent > 30)
            throw new IllegalArgumentException("Invalid sample geometry");
        double d = depthPercent / 100.0;
        double[][] boxes = {
            {0, 1 - d, .5, 1},
            {0, .5, d, 1},
            {0, 0, d, .5},
            {0, 0, 1.0 / 3, d},
            {1.0 / 3, 0, 2.0 / 3, d},
            {2.0 / 3, 0, 1, d},
            {1 - d, 0, 1, .5},
            {1 - d, .5, 1, 1},
            {.5, 1 - d, 1, 1}
        };
        int[][][] result = new int[9][1][2];
        for (int z = 0; z < 9; z++) {
            double[] b = boxes[z];
            double x = (b[0] + b[2]) / 2, y = (b[1] + b[3]) / 2;
            result[z][0][0] = Math.max(1, Math.min(width - 1, (int) Math.round(x * (width - 1))));
            result[z][0][1] = Math.max(1, Math.min(height - 1, (int) Math.round(y * (height - 1))));
        }
        return result;
    }
}
