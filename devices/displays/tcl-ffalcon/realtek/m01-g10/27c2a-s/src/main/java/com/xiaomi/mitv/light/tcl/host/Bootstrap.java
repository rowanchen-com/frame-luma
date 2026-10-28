package com.xiaomi.mitv.light.tcl.host;
import android.content.Context;
public final class Bootstrap {
 private static Controller controller;
 public Bootstrap(){}
 public synchronized String getRequest(Context ignored){
  if(controller!=null)return "[]";
  try{
   Context host=(Context)Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
   controller=new Controller(host);controller.register();
  }catch(Throwable error){android.util.Log.e("TclUnifiedHost","bootstrap",error);controller=null;}
  return "[]";
 }
 public void onResponse(Context context,String response){}
}
