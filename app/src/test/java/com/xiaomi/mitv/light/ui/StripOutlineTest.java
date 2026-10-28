package com.xiaomi.mitv.light.ui;
import com.xiaomi.mitv.light.core.EdgeLayout;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class StripOutlineTest {
    private EdgeLayout layout(boolean reversed){return new EdgeLayout(20,new int[]{3,2,2,2,2,2,2,2,3},0,0,0,reversed);}
    @Test public void defaultOutlineRetainsExactlyNineRegionsAndCoversEveryUnit() {
        List<StripOutline.Segment> segments=StripOutline.segments(layout(false));assertEquals(9,segments.size());
        assertEquals(0,segments.get(0).start,0);assertEquals(1,segments.get(8).end,0);
        for(int i=0;i<9;i++)assertEquals(i,segments.get(i).zone);
        for(int unit=0;unit<20;unit++) {
            float at=(unit+.5f)/20;int found=-2;
            for(StripOutline.Segment segment:segments)if(at>=segment.start&&at<segment.end)found=segment.zone;
            assertEquals(layout(false).zoneAt(unit),found);
        }
    }
    @Test public void reversedMappingAndDisabledTailUseTheActualLayout() {
        List<StripOutline.Segment> reversed=StripOutline.segments(layout(true));assertEquals(8,reversed.get(0).zone);
        EdgeLayout cropped=new EdgeLayout(20,new int[]{2,2,2,2,2,2,2,2,2},1,1,3,false);
        for(StripOutline.Segment segment:StripOutline.segments(cropped)){
            int unit=Math.min(19,(int)((segment.start+segment.end)*10));
            assertEquals(cropped.zoneAt(unit),segment.zone);
        }
    }
    @Test public void pathTurnsThroughCornersWithoutRoundingOrInteriorFill() {
        float[] xy=new float[2];float width=320,height=180;
        float[] distances={0,160,340,660,840,1000};
        float[][] expected={{160,180},{0,180},{0,0},{320,0},{320,180},{160,180}};
        for(int i=0;i<distances.length;i++){
            StripOutline.point(distances[i],width,height,false,xy);assertArrayEquals(expected[i],xy,.001f);
            StripOutline.point(distances[i],width,height,true,xy);assertEquals(width-expected[i][0],xy[0],.001f);
        }
    }
}
