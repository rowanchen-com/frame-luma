package frameluma.core;

/** One owner, complete frame writes, explicit session/resource lifecycle. */
public interface LightOutput extends AutoCloseable {
    /** Verify identity and length without acquiring lighting ownership or changing power. */
    LightIdentity identify() throws Exception;

    void power(boolean on) throws Exception;

    void begin() throws Exception;

    int unitCount();

    default LightIdentity identity() {
        return null;
    }

    /**
     * Borrowed RGB array is consumed before return. Brightness0 emits darkness without changing
     * power/ownership.
     */
    void send(int[] rgb, int sequence, int brightnessPercent) throws Exception;

    /** Optional per-unit sample freshness; null means the complete frame is newly sampled. */
    default void send(int[] rgb, int sequence, int brightnessPercent, boolean[] fresh)
            throws Exception {
        send(rgb, sequence, brightnessPercent);
    }

    /** Actual transport measurements; zero means the driver does not expose separate pacing. */
    default int outputIntervalMs() {
        return 0;
    }

    default int outputWriteMs() {
        return 0;
    }

    default long framesWritten() {
        return 0;
    }

    void end();

    @Override
    void close();
}
