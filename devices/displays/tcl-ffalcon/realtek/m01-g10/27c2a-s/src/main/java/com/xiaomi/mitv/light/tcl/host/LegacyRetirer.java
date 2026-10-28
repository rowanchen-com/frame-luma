package com.xiaomi.mitv.light.tcl.host;
import android.content.*;
import android.content.pm.*;
import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.util.*;
/** One-time retirement of our exact previous test package, no other receiver/cache keys. */
final class LegacyRetirer {
 private static final String LEGACY="local.mitv.normalcallercheck";
 static void retire(Context host)throws Exception {
  PackageInfo info=host.getPackageManager().getPackageInfo(LEGACY,PackageManager.GET_SIGNING_CERTIFICATES);
  Signature[] signatures=info.signingInfo.getApkContentsSigners();if(signatures.length!=1)throw new Exception("Unexpected legacy signer count");
  StringBuilder sum=new StringBuilder();for(byte v:MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray()))sum.append(String.format("%02x",v&255));
  if(!Controller.CERT.equals(sum.toString()))throw new Exception("Legacy signer differs");
  Class<?> manager=Class.forName("f.f.j.g.a.c.a",false,host.getClassLoader());
  Map<?,?> cache=cacheMap(manager);Object oldLoader=cache.get(LEGACY);
  if(oldLoader instanceof ClassLoader){
   Class<?> old=Class.forName(LEGACY+".Bootstrap",false,(ClassLoader)oldLoader);
   Field field=old.getDeclaredField("engine");field.setAccessible(true);Object engine=field.get(null);
   if(engine!=null){boolean stopped=(Boolean)engine.getClass().getMethod("stop",long.class).invoke(engine,15000L);if(!stopped)throw new Exception("Legacy engine did not stop");}
   field.set(null,null);
  }
  Context base=((ContextWrapper)host).getBaseContext();Field loaded=find(base.getClass(),"mPackageInfo");Object loadedApk=loaded.get(base);
  Field mapField=find(loadedApk.getClass(),"mReceivers");Map<?,?> receiverMap=(Map<?,?>)mapField.get(loadedApk);
  List<Context> contexts=new ArrayList<>();List<BroadcastReceiver> receivers=new ArrayList<>();
  synchronized(receiverMap){for(Map.Entry<?,?> entry:receiverMap.entrySet()){
   if(!(entry.getKey() instanceof Context)||!(entry.getValue() instanceof Map))continue;
   for(Object receiver:((Map<?,?>)entry.getValue()).keySet())
    if(receiver instanceof BroadcastReceiver&&receiver.getClass().getName().startsWith(LEGACY+".Bootstrap$")
      &&(oldLoader==null||receiver.getClass().getClassLoader()==oldLoader)){
       contexts.add((Context)entry.getKey());receivers.add((BroadcastReceiver)receiver);
      }
  }}
  for(int i=0;i<receivers.size();i++)contexts.get(i).unregisterReceiver(receivers.get(i));
  synchronized(cache){cache.remove(LEGACY);}
  android.util.Log.i("TclUnifiedHost","{\"event\":\"legacy_runtime_retired\",\"receivers\":"+receivers.size()+",\"own_cache_key_removed\":true}");
 }
 private static Field find(Class<?> type,String name)throws Exception {
  for(Class<?> current=type;current!=null;current=current.getSuperclass())try{Field field=current.getDeclaredField(name);field.setAccessible(true);return field;}catch(NoSuchFieldException ignored){}
  throw new NoSuchFieldException(name);
 }
 static Map<?,?> cacheMap(Class<?> manager)throws Exception {
  Map<?,?> result=null;
  for(Field field:manager.getDeclaredFields())if(java.lang.reflect.Modifier.isStatic(field.getModifiers())&&Map.class.isAssignableFrom(field.getType())){
   field.setAccessible(true);Object value=field.get(null);if(value==null)continue;
   if(result!=null)throw new Exception("Ambiguous host module cache");result=(Map<?,?>)value;
  }
  if(result==null)throw new Exception("Host module cache not found");return result;
 }
}
