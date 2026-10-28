package com.xiaomi.mitv.light.tcl;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;
public final class StateProvider extends ContentProvider {
 @Override public boolean onCreate(){AppCore.initialize(getContext());return true;}
 @Override public Bundle call(String method,String arg,Bundle extras){
  int uid=Binder.getCallingUid();if(uid!=1000&&uid!=Process.myUid())throw new SecurityException("Only owner or fixed system host");
  Bundle result=new Bundle();
  try{
   if("settings".equals(method))result.putString("json",AppCore.settings());
   else if("state".equals(method))result.putString("json",AppCore.state());
   else if("report".equals(method)&&uid==1000){AppCore.report(extras.getString("json","{}"));result.putBoolean("ok",true);}
   else if("apply".equals(method)&&uid==1000){
    org.json.JSONObject settings=new org.json.JSONObject(extras.getString("json","{}"));
    java.util.Iterator<String> keys=settings.keys();while(keys.hasNext()){
     String key=keys.next();if(!java.util.Arrays.asList("enabled","power","period_ms","brightness_percent","layout","locator_unit","locator_zone","locator_ttl_ms").contains(key))throw new SecurityException("Unknown app setting");
    }
    if(settings.has("layout"))new com.xiaomi.mitv.light.tcl.layout.LayoutOptions(settings.getJSONObject("layout"));
    int unit=settings.optInt("locator_unit",0),zone=settings.optInt("locator_zone",-1),ttl=settings.optInt("locator_ttl_ms",20000);
    int total=settings.has("layout")?new com.xiaomi.mitv.light.tcl.layout.LayoutOptions(settings.getJSONObject("layout")).units:AppCore.layout().units;
    if(unit<0||unit>total||zone< -1||zone>8||(unit>0&&zone>=0)||ttl<1000||ttl>30000)throw new IllegalArgumentException("Invalid bounded locator");
    if(settings.has("layout"))AppCore.setLayout(settings.getJSONObject("layout"));
    if(settings.has("locator_unit")||settings.has("locator_zone"))AppCore.locate(unit,zone,ttl);
    if(settings.has("power"))AppCore.setPower(settings.getBoolean("power"));
    if(settings.has("enabled"))AppCore.setEnabled(settings.getBoolean("enabled"));
    if(settings.has("period_ms"))AppCore.setPeriod(settings.getInt("period_ms"));
    if(settings.has("brightness_percent"))AppCore.setBrightness(settings.getInt("brightness_percent"));
    if(settings.has("enabled")||settings.has("power"))Client.flow(getContext(),AppCore.enabled()&&AppCore.power());
    result.putString("json",AppCore.settings());
   }
   else throw new SecurityException("Unsupported fixed operation");
  }catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalArgumentException(e);}
  return result;
 }
 @Override public Cursor query(Uri u,String[]p,String s,String[]a,String o){throw new UnsupportedOperationException();}
 @Override public String getType(Uri u){return "application/json";}
 @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
 @Override public int delete(Uri u,String s,String[]a){throw new UnsupportedOperationException();}
 @Override public int update(Uri u,ContentValues v,String s,String[]a){throw new UnsupportedOperationException();}
}
