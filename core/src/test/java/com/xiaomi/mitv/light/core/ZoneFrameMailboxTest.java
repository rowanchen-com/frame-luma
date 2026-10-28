package com.xiaomi.mitv.light.core;
import org.junit.Test;
import static org.junit.Assert.*;

public class ZoneFrameMailboxTest {
    @Test public void expiredOrFutureFramesCannotReplaceTheDeviceSource() {
        ZoneFrameMailbox box=new ZoneFrameMailbox();box.publish(new int[9],1000,"layout");
        assertNull(box.read(999));assertNotNull(box.read(1600));assertNull(box.read(1601));
        box.clear();assertNull(box.read(1200));
    }
    @Test public void callerAndConsumerCannotMutateStoredPixels() {
        ZoneFrameMailbox box=new ZoneFrameMailbox();int[] rgb=new int[9];rgb[0]=0xff0000;
        box.publish(rgb,1000,"layout");rgb[0]=0;
        ZoneFrameMailbox.Frame frame=box.read(1001);assertEquals(0xff0000,frame.colors[0]);
        frame.colors[0]=0;assertEquals(0xff0000,box.read(1002).colors[0]);
    }
    @Test(expected=IllegalArgumentException.class) public void incompleteUiFramesAreRejected() {
        new ZoneFrameMailbox().publish(new int[3],1000,"layout");
    }
}
