package com.xiaomi.mitv.light.tcl.layout;
import com.xiaomi.mitv.light.core.EdgeLayout;
import org.json.*;

/** Bounded runtime configuration. Original keys remain readable during an in-place upgrade. */
public final class LayoutOptions {
    public final String mode;
    public final int[] counts;
    public final int offHead,offTail,offset,samples,depth,units;
    public final boolean reverse;
    public LayoutOptions(JSONObject j)throws JSONException {
        mode=j.optString("mode","four_edge9");
        if(!mode.equals("four_edge9")&&!mode.equals("legacy3"))throw new IllegalArgumentException("Unknown layout mode");
        JSONArray a=j.getJSONArray("counts");if(a.length()!=9)throw new IllegalArgumentException("Nine counts required");
        counts=new int[9];int sum=0;for(int i=0;i<9;i++){counts[i]=a.getInt(i);sum+=counts[i];}
        offHead=j.optInt("off_head",0);offTail=j.optInt("off_tail",0);offset=j.optInt("offset_units",0);
        units=j.optInt("unit_count",sum+offHead+offTail);samples=j.optInt("samples_per_zone",1);depth=j.optInt("depth_percent",16);reverse=j.optBoolean("reverse",false);
        if((samples!=1&&samples!=3)||depth<5||depth>30)throw new IllegalArgumentException("Invalid sampling configuration");geometry();
    }
    public EdgeLayout geometry(){return new EdgeLayout(units,counts,offHead,offTail,offset,reverse);}
    public JSONObject json()throws JSONException {
        JSONObject j=new JSONObject();j.put("mode",mode);j.put("unit_count",units);JSONArray a=new JSONArray();for(int n:counts)a.put(n);j.put("counts",a);
        j.put("off_head",offHead);j.put("off_tail",offTail);j.put("offset_units",offset);j.put("reverse",reverse);j.put("samples_per_zone",samples);j.put("depth_percent",depth);return j;
    }
}
