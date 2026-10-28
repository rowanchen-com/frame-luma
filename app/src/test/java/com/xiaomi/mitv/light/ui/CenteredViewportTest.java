package com.xiaomi.mitv.light.ui;
import org.junit.Test;
import static org.junit.Assert.*;

public class CenteredViewportTest {
    @Test public void allControlsFitCenteredAtHdQhdAndUhd() {
        for(int[] window:new int[][]{{1280,720},{1920,1080},{2560,1440},{3840,2160},{1920,1000}}) {
            CenteredViewport fit=CenteredViewport.fit(window[0],window[1]);
            assertTrue(fit.left>=window[0]*.149f);assertTrue(fit.top>=window[1]*.149f);
            assertEquals(window[0]/2f,fit.left+fit.width/2f,1f);
            assertEquals(window[1]/2f,fit.top+fit.height/2f,1f);
            assertTrue(fit.left+fit.width<window[0]);assertTrue(fit.top+fit.height<window[1]);
            assertEquals(CenteredViewport.WIDTH/(float)CenteredViewport.HEIGHT,fit.width/(float)fit.height,.004f);
        }
    }
    @Test public void fourKScalesTheCompleteDesignInsteadOfClippingFixedDpRows() {
        CenteredViewport hd=CenteredViewport.fit(1920,1080),uhd=CenteredViewport.fit(3840,2160);
        assertEquals(hd.unit*2,uhd.unit,.001f);assertEquals(hd.width*2,uhd.width,1);
    }
}
