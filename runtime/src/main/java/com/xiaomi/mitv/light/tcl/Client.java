package com.xiaomi.mitv.light.tcl;
import android.content.Context;
import android.content.Intent;
import com.xiaomi.mitv.light.service.TvLightService;
public final class Client {
 public static void send(Context context,String action){
  Intent value=new Intent(AppCore.PKG+".tcl."+action);value.setPackage("com.tcl.ttvs");context.sendBroadcast(value);
 }
 public static void refresh(Context context){send(context,"CONFIG");}
 public static void flow(Context context,boolean enabled){
  Intent intent=new Intent(context,TvLightService.class);intent.setAction(enabled?TvLightService.ACTION_START_FLOW:TvLightService.ACTION_STOP_FLOW);
  if(enabled&&android.os.Build.VERSION.SDK_INT>=26)context.startForegroundService(intent);else context.startService(intent);
 }
}
