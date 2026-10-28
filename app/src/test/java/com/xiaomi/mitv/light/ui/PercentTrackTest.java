package com.xiaomi.mitv.light.ui;
import org.junit.Test;
import static org.junit.Assert.*;

public class PercentTrackTest {
    @Test public void everyPercentageIsReachableWithProtectedThumbMargins() {
        for(int width:new int[]{285,380,570}){
            int inset=21;
            for(int percent=0;percent<=100;percent++){
                float x=inset+(width-inset*2)*percent/100f;
                assertEquals(percent,PercentTrack.at(x,width,inset,inset));
            }
        }
    }
    @Test public void actualMidpointAndTouchBeyondEdgesAreExact() {
        assertEquals(50,PercentTrack.at(143,285,21,21));
        assertEquals(0,PercentTrack.at(0,285,21,21));
        assertEquals(100,PercentTrack.at(285,285,21,21));
        // There is no remembered grab offset: visiting near the center cannot change its value.
        PercentTrack.at(140,285,21,21);
        assertEquals(50,PercentTrack.at(143,285,21,21));
    }
}
