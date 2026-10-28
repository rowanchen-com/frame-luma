package com.xiaomi.mitv.light.core;

/** One owner, complete frame writes, explicit session/resource lifecycle. */
public interface LightOutput extends AutoCloseable {
    void begin() throws Exception;
    int unitCount();
    void send(int[] rgb, int sequence, int brightnessPercent) throws Exception;
    void end();
    @Override void close();
}
