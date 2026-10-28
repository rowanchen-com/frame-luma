package com.xiaomi.mitv.light.ui;
import org.junit.Test;
import static org.junit.Assert.*;

public class AuroraMotionTest {
    @Test public void aLightMovesClockwiseInsteadOfSlidingAcrossTheScreen() {
        assertEquals(196,AuroraMotion.x(0,0),.001);assertEquals(54,AuroraMotion.y(0,0),.001);
        float quarter=AuroraMotion.ORBIT_SECONDS/4;
        assertEquals(96,AuroraMotion.x(quarter,0),.001);assertEquals(116,AuroraMotion.y(quarter,0),.001);
        assertEquals(-4,AuroraMotion.x(quarter*2,0),.001);assertEquals(54,AuroraMotion.y(quarter*2,0),.001);
        assertEquals(96,AuroraMotion.x(quarter*3,0),.001);assertEquals(-8,AuroraMotion.y(quarter*3,0),.001);
        assertEquals(AuroraMotion.x(0,0),AuroraMotion.x(AuroraMotion.ORBIT_SECONDS,0),.001);
    }
    @Test public void theCloudsCoverTheCompleteHueCircleWithoutASingleRainbowEdge() {
        boolean[] bins=new boolean[12];
        for(int second=0;second<52;second++)for(int light=0;light<AuroraMotion.COUNT;light++)bins[(int)(AuroraMotion.hue(second,light)/30)]=true;
        for(boolean present:bins)assertTrue(present);
        assertTrue(AuroraMotion.hue(.1,0)>AuroraMotion.hue(0,0));
    }
    @Test public void movementIsVisibleWithinTwoSecondsForASyncPreview(){
        double dx=AuroraMotion.x(2,0)-AuroraMotion.x(0,0),dy=AuroraMotion.y(2,0)-AuroraMotion.y(0,0);
        assertTrue(Math.sqrt(dx*dx+dy*dy)>30);assertTrue(AuroraMotion.hue(1,0)>=20);
    }
}
