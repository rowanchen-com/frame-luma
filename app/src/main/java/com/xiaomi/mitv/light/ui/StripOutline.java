package com.xiaomi.mitv.light.ui;

import com.xiaomi.mitv.light.core.EdgeLayout;
import java.util.ArrayList;
import java.util.List;

/** Display-only runs of the actual physical mapping. Adjacent equal colors never erase a region boundary. */
public final class StripOutline {
    public static final class Segment {
        public final int zone;
        public final float start, end;
        private Segment(int zone,float start,float end){this.zone=zone;this.start=start;this.end=end;}
    }
    private StripOutline() { }
    public static List<Segment> segments(EdgeLayout layout) {
        List<Segment> result=new ArrayList<>();int count=layout.unitCount();
        int start=0,zone=layout.zoneAt(0);
        for(int unit=1;unit<=count;unit++) {
            int next=unit==count?-2:layout.zoneAt(unit);
            if(next!=zone){result.add(new Segment(zone,start/(float)count,unit/(float)count));start=unit;zone=next;}
        }
        return result;
    }
    /** Clockwise screen coordinates from bottom-center towards the left. */
    public static void point(float distance,float width,float height,boolean reversed,float[] out) {
        float perimeter=2*(width+height),d=Math.max(0,Math.min(perimeter,distance));
        float x,y;
        if(d<=width/2){x=width/2-d;y=height;}
        else if((d-=width/2)<=height){x=0;y=height-d;}
        else if((d-=height)<=width){x=d;y=0;}
        else if((d-=width)<=height){x=width;y=d;}
        else {d-=height;x=width-Math.min(d,width/2);y=height;}
        out[0]=reversed?width-x:x;out[1]=y;
    }
}
