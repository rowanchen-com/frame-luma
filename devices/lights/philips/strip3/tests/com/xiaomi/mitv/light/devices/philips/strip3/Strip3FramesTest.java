package com.xiaomi.mitv.light.devices.philips.strip3;
import org.junit.Test;
import static org.junit.Assert.*;

public class Strip3FramesTest {
    @Test public void blackFrameCoversTwentyAndSplitsLongRun(){
        assertTrue(Strip3Frames.encode(new int[20],1,25).endsWith("000000 f 000000 5"));
    }
    @Test public void everyLocatorAddressesExactlyOneUnit(){
        for(int chosen=0;chosen<20;chosen++){
            int[] rgb=new int[20];rgb[chosen]=0xffffff;
            String[] tokens=Strip3Frames.encode(rgb,chosen,25).split(" ");int at=0,lit=0;
            for(int i=4;i<tokens.length;i+=2){int count=Integer.parseInt(tokens[i+1],16);assertTrue(count<=15);
                if(!tokens[i].equals("000000")){assertEquals(chosen,at);assertEquals(1,count);lit+=count;}at+=count;}
            assertEquals(20,at);assertEquals(1,lit);
        }
    }
    @Test(expected=IllegalArgumentException.class) public void rejectOversizedArbitraryFrame(){
        int[] rgb=new int[20];for(int i=0;i<20;i++)rgb[i]=i+1;Strip3Frames.encode(rgb,1,25);
    }
    @Test(expected=IllegalArgumentException.class) public void otherLengthIsNotSilentlyClaimed(){Strip3Frames.encode(new int[40],1,25);}
}
