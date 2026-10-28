package com.xiaomi.mitv.light.runtime;
import android.content.Context;

/** Fixed compatibility call used by the existing, already-installed TCL host core. */
public final class LightAccess {
    private LightAccess() {}
    public static void power(Context host,boolean on)throws Exception{Devices.light(host).power(host,on);}
    public static boolean present(Context context){try{Devices.light(context);return true;}catch(Exception e){return false;}}
}
