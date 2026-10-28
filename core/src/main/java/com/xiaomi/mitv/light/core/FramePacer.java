package com.xiaomi.mitv.light.core;

public final class FramePacer {
    private FramePacer() {}
    public static int normalizeLegacyPeriod(int ms) {
        if(ms<150||ms>2000)throw new IllegalArgumentException("Period outside supported bounds");
        return ms;
    }
    public static long remainingMs(int periodMs,long elapsedMs) {
        return Math.max(0,periodMs-Math.max(0,elapsedMs));
    }
}
