package com.xiaomi.mitv.light.devices.philips.strip3;
import android.content.Context;
import android.hardware.usb.*;
import com.xiaomi.mitv.light.core.LightOutput;
import com.xiaomi.mitv.light.core.EdgeLayout;
import com.xiaomi.mitv.light.deviceapi.LightModule;

public final class PhilipsStrip3Module implements LightModule {
    @Override public String id(){return "philips-strip3";}
    @Override public String displayName(){return "米家追光氛围灯带";}
    @Override public boolean present(Context context) {
        UsbManager usb=(UsbManager)context.getSystemService(Context.USB_SERVICE);
        for(UsbDevice d:usb.getDeviceList().values())if(d.getVendorId()==0x1a86&&d.getProductId()==0x7523)return true;
        return false;
    }
    @Override public LightOutput open(Context context)throws Exception{return new Strip3Session(context);}
    @Override public void power(Context context,boolean on)throws Exception{Strip3Session.power(context,on);}
    @Override public String defaultLayoutAsset(){return "lights/philips-strip3/layouts/four-sides.json";}
    @Override public void validateLayout(EdgeLayout layout){
        int[] colors=new int[9];for(int i=0;i<9;i++)colors[i]=0x102030+i*0x010101;
        Strip3Frames.encode(layout.map(colors),1,25);
    }
}
