package frameluma.core;

public final class FramePacer {
    private static final int[] SPEED_PERIODS = {200, 180, 150};
    public static final int DEFAULT_SPEED_INDEX = SPEED_PERIODS.length - 1;
    public static final int DEFAULT_PERIOD_MS = SPEED_PERIODS[DEFAULT_SPEED_INDEX];

    private FramePacer() {}

    /** UI order is low, medium, fast; only this class owns the preset values. */
    public static int speedPeriod(int index) {
        return SPEED_PERIODS[index];
    }

    /** Resolve any supported period to the nearest available preset without writing settings. */
    public static int speedIndex(int ms) {
        validatePeriod(ms);
        int nearest = 0;
        for (int i = 1; i < SPEED_PERIODS.length; i++)
            if (Math.abs(SPEED_PERIODS[i] - ms) < Math.abs(SPEED_PERIODS[nearest] - ms))
                nearest = i;
        return nearest;
    }

    public static int validatePeriod(int ms) {
        if (ms < SPEED_PERIODS[SPEED_PERIODS.length - 1] || ms > 2000)
            throw new IllegalArgumentException("Period outside supported bounds");
        return ms;
    }

    public static long remainingMs(int periodMs, long elapsedMs) {
        return Math.max(0, periodMs - Math.max(0, elapsedMs));
    }

    /** Sleep until the frame deadline or an actual configuration/stop change, without polling. */
    public static final class Waiter {
        private long revision;

        public synchronized long stamp() {
            return revision;
        }

        public synchronized void wake() {
            revision++;
            notifyAll();
        }

        public synchronized void await(long observed, long delayMs) throws InterruptedException {
            if (delayMs <= 0 || revision != observed) return;
            long deadline = System.nanoTime() + Math.min(delayMs, 2000) * 1000000L;
            while (revision == observed) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) return;
                wait(remaining / 1000000L, (int) (remaining % 1000000L));
            }
        }
    }
}
