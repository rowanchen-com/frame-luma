package com.xiaomi.mitv.light.runtime;
import android.content.Context;
import com.xiaomi.mitv.light.core.*;
import com.xiaomi.mitv.light.deviceapi.*;

public final class Devices {
    private Devices() {}
    private static final DeviceModule[] MODULES=GeneratedDevices.create();
    public static DisplayModule display(Context context)throws Exception {
        DisplayModule selected=null;
        for(DeviceModule module:MODULES)if(module instanceof DisplayModule){
            DisplayModule d=(DisplayModule)module;
            if(d.matches(d.inspect(context))){if(selected!=null)throw new Exception("Conflicting device matches");selected=d;}
        }
        if(selected==null)throw new Exception("尚未找到经过验证的机型适配，不能仅凭C3APRO判定型号");return selected;
    }
    public static LightModule light(Context context)throws Exception {
        LightModule selected=null;
        for(DeviceModule module:MODULES)if(module instanceof LightModule&&((LightModule)module).present(context)){
            if(selected!=null)throw new Exception("Multiple light devices need explicit selection");selected=(LightModule)module;
        }
        if(selected==null)throw new Exception("没有找到已支持的灯光设备");return selected;
    }
    public static LightModule previewLight(){
        for(DeviceModule m:MODULES)if(m instanceof LightModule)return (LightModule)m;
        throw new IllegalStateException("No light module compiled");
    }
}
