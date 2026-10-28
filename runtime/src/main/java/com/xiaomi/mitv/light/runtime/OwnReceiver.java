package com.xiaomi.mitv.light.runtime;
import android.content.*;
import com.xiaomi.mitv.light.tcl.*;
public final class OwnReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        AppCore.initialize(context);
        if(AppCore.enabled()&&AppCore.power())Client.flow(context,true);
    }
}
