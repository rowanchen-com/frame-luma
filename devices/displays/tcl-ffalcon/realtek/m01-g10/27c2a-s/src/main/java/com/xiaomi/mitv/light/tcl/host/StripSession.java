package com.xiaomi.mitv.light.tcl.host;
import android.content.Context;

/** Keeps the installed Controller's fixed binary entry while output belongs to the selected light module. */
final class StripSession {
    static void setPower(Context context,boolean value)throws Exception {
        Class<?> access=Class.forName("com.xiaomi.mitv.light.runtime.LightAccess",true,StripSession.class.getClassLoader());
        access.getMethod("power",Context.class,boolean.class).invoke(null,context,value);
    }
}
