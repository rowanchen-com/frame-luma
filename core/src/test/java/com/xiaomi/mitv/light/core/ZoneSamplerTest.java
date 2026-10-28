package com.xiaomi.mitv.light.core;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class ZoneSamplerTest {
    private static final int[][][] POINTS = points();
    private static int[][][] points() {
        int[][][] points = new int[9][1][2];
        for (int zone = 0; zone < 9; zone++) points[zone][0][0] = zone;
        return points;
    }
    private static final class Source implements PixelSource {
        long now;
        int generation = 1, reads, begins;
        public int width() { return 9; }
        public int height() { return 1; }
        public void beginFrame() { begins++; }
        public int readRgb(int x, int y) { now += 30; reads++; return (generation << 16) | x; }
        public void close() { }
    }
    @Test public void progressiveOutputDoesNotWaitForAllNineSlowReads() throws Exception {
        Source source = new Source();
        ZoneSampler sampler = new ZoneSampler();
        sampler.sample(source, POINTS, true, () -> true, (colors, done) -> { });
        source.now = 0; source.generation = 2;
        List<Long> sentAt = new ArrayList<>();
        List<int[]> snapshots = new ArrayList<>();
        sampler.sample(source, POINTS, true, () -> true, (colors, done) -> {
            sentAt.add(source.now); snapshots.add(colors.clone()); source.now += 5;
        });
        assertTrue("First USB update still waits for the complete 270ms sweep: " + sentAt,
                sentAt.get(0) <= 100);
        assertEquals(3, sentAt.size());
        assertEquals(18, source.reads);
        for (int z = 0; z < 9; z++) assertEquals((2 << 16) | z, snapshots.get(2)[z]);
    }
    @Test public void coldStartAndFrameBackendsPublishOnlyCompleteFrames() throws Exception {
        Source source = new Source(); ZoneSampler sampler = new ZoneSampler();
        List<Integer> done = new ArrayList<>();
        sampler.sample(source, POINTS, true, () -> true, (c, n) -> done.add(n));
        assertEquals(java.util.Arrays.asList(9), done);
        done.clear();
        sampler.sample(source, POINTS, false, () -> true, (c, n) -> done.add(n));
        assertEquals(java.util.Arrays.asList(9), done);
        assertEquals(2, source.begins);
    }
    @Test public void cancellationDoesNotSendAnUnfinishedFrame() throws Exception {
        Source source = new Source(); ZoneSampler sampler = new ZoneSampler();
        sampler.sample(source, POINTS, true, () -> true, (c, n) -> { });
        int[] outputs = {0}; source.reads = 0;
        assertFalse(sampler.sample(source, POINTS, true, () -> source.reads < 2,
                (c, n) -> outputs[0]++));
        assertEquals(0, outputs[0]);
    }
}
