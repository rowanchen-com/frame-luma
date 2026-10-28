package com.xiaomi.mitv.light.tcl;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import org.json.JSONObject;
import com.xiaomi.mitv.light.tcl.layout.LayoutOptions;
public final class AppCore {
 public static final String PKG="com.xiaomi.mitv.light",PERMISSION=PKG+".permission.CONTROL_INTERNAL";
 public static final Uri STATE_URI=Uri.parse("content://"+PKG+".tcl.state/state");
 private static Context context;
 private static volatile String state="{\"ready\":false,\"running\":false}";
 private static int locatorUnit,locatorZone=-1;
 private static long locatorUntil;
 private static volatile boolean uiVisible;
 private static final com.xiaomi.mitv.light.core.ZoneFrameMailbox uiFrames=new com.xiaomi.mitv.light.core.ZoneFrameMailbox();
 private static volatile String defaultLayout;
 public static synchronized void initialize(Context value){if(context==null)context=value.getApplicationContext();}
 public static Context context(){return context;}
 private static SharedPreferences prefs(){return context.getSharedPreferences("tcl_light",Context.MODE_PRIVATE);}
 public static boolean enabled(){return prefs().getBoolean("enabled",false);}
 public static boolean power(){return prefs().getBoolean("power",true);}
 public static int brightness(){return prefs().getInt("brightness",25);}
 public static int period(){return com.xiaomi.mitv.light.core.FramePacer.normalizeLegacyPeriod(prefs().getInt("period",150));}
 public static void setEnabled(boolean value){
  if(value==enabled()&&(!value||power()))return;
  SharedPreferences.Editor e=prefs().edit().putBoolean("enabled",value);if(value)e.putBoolean("power",true);else clearLocator();e.apply();changed();
 }
 public static void setPower(boolean value){
  if(value==power()&&(value||!enabled()))return;
  SharedPreferences.Editor e=prefs().edit().putBoolean("power",value);if(!value){e.putBoolean("enabled",false);clearLocator();}e.apply();changed();
 }
 public static void setBrightness(int value){value=Math.max(0,Math.min(100,value));if(value!=brightness()){prefs().edit().putInt("brightness",value).apply();changed();}}
 public static void setPeriod(int value){value=com.xiaomi.mitv.light.core.FramePacer.normalizeLegacyPeriod(value);if(value!=period()){prefs().edit().putInt("period",value).apply();changed();}}
 public static void setUiVisible(boolean value){uiVisible=value;if(!value)uiFrames.clear();}
 public static void publishUiFrame(int[] colors,String layout){if(uiVisible)uiFrames.publish(colors,android.os.SystemClock.elapsedRealtime(),layout);}
 public static android.os.Bundle uiFrame(){
  android.os.Bundle result=new android.os.Bundle();result.putBoolean("visible",uiVisible);
  com.xiaomi.mitv.light.core.ZoneFrameMailbox.Frame frame=uiVisible?uiFrames.read(android.os.SystemClock.elapsedRealtime()):null;
  if(frame!=null){result.putIntArray("colors",frame.colors);result.putString("layout",frame.layout);result.putLong("at",frame.at);}
  return result;
 }
 public static LayoutOptions layout(){try{
  String stored=prefs().getString("layout",null);
  if(stored==null&&defaultLayout!=null)stored=defaultLayout;
  if(stored==null){String path=com.xiaomi.mitv.light.runtime.Devices.previewLight().defaultLayoutAsset();
   try(java.io.InputStream in=context.getAssets().open(path);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){
    byte[] b=new byte[1024];int n;while((n=in.read(b))>=0)out.write(b,0,n);stored=out.toString("UTF-8");defaultLayout=stored;
   }
  }JSONObject value=new JSONObject(stored);
  // The old diagnostic three-point mode is retired; keep its physical layout/settings for the nine-zone source version.
  if("legacy3".equals(value.optString("mode")))value.put("mode","four_edge9");
  return new LayoutOptions(value);
 }catch(Exception e){throw new IllegalStateException("Invalid saved layout",e);}}
 public static void setLayout(JSONObject value)throws Exception{
  LayoutOptions validated=new LayoutOptions(value);com.xiaomi.mitv.light.runtime.Devices.previewLight().validateLayout(validated.geometry());
  String text=validated.json().toString();if(!text.equals(prefs().getString("layout",null)))prefs().edit().putString("layout",text).apply();clearLocator();changed();
 }
 public static void resetLayout(){prefs().edit().remove("layout").apply();clearLocator();changed();}
 public static synchronized void locate(int unit,int zone,int ttl){
  if(unit<0||unit>layout().units||zone< -1||zone>8||(unit>0&&zone>=0)||ttl<1000||ttl>30000)throw new IllegalArgumentException("定位参数范围");
  locatorUnit=unit;locatorZone=zone;locatorUntil=unit>0||zone>=0?android.os.SystemClock.elapsedRealtime()+ttl:0;changed();
 }
 public static synchronized void clearLocator(){locatorUnit=0;locatorZone=-1;locatorUntil=0;}
 public static synchronized String settings(){JSONObject j=new JSONObject();try{
  // Frozen installed host validates 1..100. The new engine separately reads the logical 0..100 value.
  j.put("enabled",enabled());j.put("power",power());j.put("period_ms",period());j.put("brightness_percent",Math.max(1,brightness()));j.put("user_brightness_percent",brightness());j.put("layout",layout().json());j.put("ui_visible",uiVisible);
  if(android.os.SystemClock.elapsedRealtime()>=locatorUntil)clearLocator();
  j.put("locator_unit",locatorUnit);j.put("locator_zone",locatorZone);j.put("locator_until_elapsed",locatorUntil);
 }catch(Exception e){throw new IllegalStateException(e);}return j.toString();}
 public static String state(){return state;}
 public static void report(String value)throws Exception {if(value.length()>8192)throw new Exception("status too long");new JSONObject(value);state=value;changed();}
 public static void changed(){if(context!=null)context.getContentResolver().notifyChange(STATE_URI,null);}
}
