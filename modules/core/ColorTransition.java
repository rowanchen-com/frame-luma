package frameluma.core;

import java.util.Arrays;

/**
 * Fixed-size, latest-target interpolation. Retargeting starts from the current colour, not a queue.
 */
public final class ColorTransition {
    private final int[] from, target, current;
    private final long[] darkSince, started;
    private final int duration, blackHold, darkThreshold;
    private boolean seeded;

    public ColorTransition(int count, int durationMs) {
        this(count, durationMs, 0, 0);
    }

    public ColorTransition(int count, int durationMs, int blackHoldMs, int darkThreshold) {
        if (count < 1
                || durationMs < 0
                || blackHoldMs < 0
                || darkThreshold < 0
                || darkThreshold > 255) throw new IllegalArgumentException("Invalid transition");
        from = new int[count];
        target = new int[count];
        current = new int[count];
        darkSince = new long[count];
        started = new long[count];
        Arrays.fill(darkSince, -1);
        duration = durationMs;
        blackHold = blackHoldMs;
        this.darkThreshold = darkThreshold;
    }

    public boolean target(int[] colors, long now) {
        return target(colors, now, null);
    }

    /** Freshness describes actual new samples, not retained regions in a partial update. */
    public boolean target(int[] colors, long now, boolean[] fresh) {
        if (colors.length != target.length)
            throw new IllegalArgumentException("Colour count changed");
        if (fresh != null && fresh.length != target.length)
            throw new IllegalArgumentException("Freshness count changed");
        render(now, current);
        boolean changed = !seeded;
        for (int index = 0; index < colors.length; index++) {
            if (seeded && fresh != null && !fresh[index]) continue;
            int color = colors[index];
            if (seeded && blackHold > 0 && dark(color) && !dark(target[index])) {
                if (darkSince[index] < 0) darkSince[index] = now;
                if (now - darkSince[index] < blackHold) color = target[index];
            } else darkSince[index] = -1;
            if (!seeded || color != target[index]) {
                from[index] = seeded ? current[index] : color;
                target[index] = color;
                current[index] = from[index];
                started[index] = now;
                changed = true;
            }
        }
        seeded = true;
        return changed;
    }

    private boolean dark(int color) {
        return ((color >>> 16) & 255) <= darkThreshold
                && ((color >>> 8) & 255) <= darkThreshold
                && (color & 255) <= darkThreshold;
    }

    public void replace(int[] colors, long now) {
        System.arraycopy(colors, 0, target, 0, target.length);
        Arrays.fill(darkSince, -1);
        finish(current);
        seeded = true;
        Arrays.fill(started, now);
    }

    public boolean moving(long now) {
        if (!seeded) return false;
        for (int index = 0; index < target.length; index++)
            if (from[index] != target[index] && now - started[index] < duration) return true;
        return false;
    }

    public void finish(int[] into) {
        System.arraycopy(target, 0, from, 0, target.length);
        System.arraycopy(target, 0, current, 0, target.length);
        System.arraycopy(target, 0, into, 0, target.length);
    }

    public void render(long now, int[] into) {
        if (into.length != target.length)
            throw new IllegalArgumentException("Colour count changed");
        for (int index = 0; index < target.length; index++) {
            float t =
                    duration == 0
                            ? 1f
                            : Math.max(0f, Math.min(1f, (now - started[index]) / (float) duration));
            t = t * t * (3f - 2f * t);
            int a = from[index], b = target[index], color = 0;
            for (int shift = 0; shift <= 16; shift += 8) {
                int start = (a >>> shift) & 255, end = (b >>> shift) & 255;
                color |= Math.round(start + (end - start) * t) << shift;
            }
            into[index] = color;
        }
    }
}
