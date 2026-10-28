package com.xiaomi.mitv.light.devices.philips.strip3;
import java.util.Locale;

/** Validated strip3 PC protocol; lengths are hex and every physical unit is covered. */
public final class Strip3Frames {
    private Strip3Frames() {}
    public static String encode(int[] rgb,int sequence,int brightness) {
        if(rgb==null||rgb.length!=20||brightness<0||brightness>100)throw new IllegalArgumentException("Verified 2m frame required");
        // Zero is an all-black frame at the already-validated wire brightness 1, not a new firmware command.
        if(brightness==0){for(int color:rgb)if(color<0||color>0xffffff)throw new IllegalArgumentException("RGB range");return String.format(Locale.ROOT,"set_rgb_pc %04x 00 01 000000 f 000000 5",sequence&65535);}
        StringBuilder out=new StringBuilder(String.format(Locale.ROOT,"set_rgb_pc %04x 00 %02x",sequence&65535,brightness));
        for(int start=0;start<rgb.length;) {
            int color=rgb[start];if(color<0||color>0xffffff)throw new IllegalArgumentException("RGB range");
            int end=start+1;while(end<rgb.length&&end-start<15&&rgb[end]==color)end++;
            out.append(String.format(Locale.ROOT," %06x %x",color,end-start));start=end;
        }
        if(out.length()+2>=120)throw new IllegalArgumentException("Strip frame exceeds verified limit");return out.toString();
    }
}
