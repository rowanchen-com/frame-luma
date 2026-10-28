package com.xiaomi.mitv.light.core;

/** Nine logical regions; the physical unit count is provided by the light device. */
public final class EdgeLayout {
    public static final String[] LABELS={"左下底边","左侧下半","左侧上半","顶边左段","顶边中段","顶边右段","右侧上半","右侧下半","右下底边"};
    private final int total,head,tail,offset;
    private final int[] counts;
    private final boolean reversed;
    public EdgeLayout(int total,int[] counts,int head,int tail,int offset,boolean reversed) {
        if(total<1||total>4096||counts==null||counts.length!=9||head<0||tail<0||offset<0||offset>=total)
            throw new IllegalArgumentException("Invalid physical layout");
        int sum=head+tail;
        for(int value:counts){if(value<0||value>total)throw new IllegalArgumentException("Invalid region count");sum+=value;}
        if(sum!=total)throw new IllegalArgumentException("九区加关闭段必须合计"+total+"，当前"+sum);
        this.total=total;this.counts=counts.clone();this.head=head;this.tail=tail;this.offset=offset;this.reversed=reversed;
    }
    public int unitCount(){return total;}
    public int zoneAt(int physical) {
        if(physical<0||physical>=total)throw new IllegalArgumentException("Physical unit out of range");
        int unit=(physical-offset+total)%total;if(reversed)unit=total-1-unit;
        if(unit<head||unit>=total-tail)return -1;unit-=head;
        for(int zone=0;zone<counts.length;zone++){if(unit<counts[zone])return zone;unit-=counts[zone];}
        throw new IllegalStateException("Unassigned physical unit");
    }
    public int[] map(int[] zones) {
        if(zones==null||zones.length!=9)throw new IllegalArgumentException("Nine colors required");
        int[] units=new int[total];for(int i=0;i<total;i++){int zone=zoneAt(i);units[i]=zone<0?0:zones[zone]&0xffffff;}return units;
    }
    public int[] locate(int oneBasedUnit) {
        if(oneBasedUnit<1||oneBasedUnit>total)throw new IllegalArgumentException("Invalid locator unit");
        int[] units=new int[total];units[oneBasedUnit-1]=0xffffff;return units;
    }
    public int[] markZone(int zone) {
        if(zone<0||zone>=9)throw new IllegalArgumentException("Invalid locator region");
        int[] colors=new int[9];colors[zone]=0xffffff;return map(colors);
    }
}
