package com.xiaomi.mitv.light.core;
import org.junit.Test;
import static org.junit.Assert.*;

public class EngineContractsTest {
    @Test public void exactModelRequiresProjectId() {
        MatchRule rule=new MatchRule("TCL","M01","G10","C3APRO","rtd2885p","V064",32);
        assertTrue(rule.matches(new DeviceIdentity("TCL","M01","G10","C3APRO","rtd2885p","build-V064",32)));
        assertFalse(rule.matches(new DeviceIdentity("TCL","M01","G10","C3APRO","rtd2885p","build-V064",null)));
        assertFalse(rule.matches(new DeviceIdentity("TCL","M01","G10","C3APRO","rtd2885p","build-V064",33)));
    }
    @Test public void nineZonesCoverEveryPhysicalUnit() {
        EdgeLayout l=new EdgeLayout(20,new int[]{3,2,2,2,2,2,2,2,3},0,0,0,false);
        int[] expected={0,0,0,1,1,2,2,3,3,4,4,5,5,6,6,7,7,8,8,8};
        for(int i=0;i<20;i++)assertEquals(expected[i],l.zoneAt(i));
        for(int u=1;u<=20;u++){int[] frame=l.locate(u);for(int i=0;i<20;i++)assertEquals(i==u-1?0xffffff:0,frame[i]);}
    }
    @Test public void lengthIsNotGloballyHardcodedToTwoMeters() {
        EdgeLayout l=new EdgeLayout(40,new int[]{6,4,4,4,4,4,4,4,6},0,0,0,false);
        assertEquals(40,l.map(new int[9]).length);
    }
    @Test public void pacingCannotOutrunCaptureCost() {
        assertEquals(0,FramePacer.remainingMs(300,420));
        assertEquals(230,FramePacer.remainingMs(500,270));
        assertEquals(730,FramePacer.remainingMs(1000,270));
        assertEquals(150,FramePacer.normalizeLegacyPeriod(150));
    }
    @Test public void samplePointsUsePanelNotUiCoordinates() {
        int[][][] p=EdgeRegions.points(3840,2160,3,16);
        assertEquals(1920,p[4][1][0]);assertTrue(p[0][0][1]>1800);
        for(int[][] zone:p)for(int[] xy:zone){assertTrue(xy[0]>0&&xy[0]<3840);assertTrue(xy[1]>0&&xy[1]<2160);}
    }
    @Test public void frameBackendAcquiresOnceAndMapsPanelCoordinates()throws Exception {
        final int[] calls={0};
        FramePixelSource source=new FramePixelSource(){
            public int width(){return 3840;}public int height(){return 2160;}
            protected RgbFrame captureFrame(){calls[0]++;return new RgbFrame(2,1,new int[]{0xff0000,0x0000ff});}
        };
        source.beginFrame();assertEquals(0xff0000,source.readRgb(0,0));assertEquals(0x0000ff,source.readRgb(3839,2159));assertEquals(1,calls[0]);
    }
}
