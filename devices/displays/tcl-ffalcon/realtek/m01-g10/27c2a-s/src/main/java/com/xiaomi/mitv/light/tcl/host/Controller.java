package com.xiaomi.mitv.light.tcl.host;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import android.os.Process;
import android.util.Log;
import dalvik.system.DexClassLoader;
import java.io.FileInputStream;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.concurrent.*;
import org.json.JSONObject;

/** One installed package, fixed operations, separate normal-UID UI and system host. */
final class Controller {
 static final String PKG="com.xiaomi.mitv.light",PREFIX=PKG+".tcl.",PERM=PKG+".permission.CONTROL_INTERNAL";
 static final String CERT="1d94625a49044ae282919a401a99c9e83697a22e166df8cc337a871bf23ec4b2";
 static final Uri STATE=Uri.parse("content://"+PKG+".tcl.state/state");
 private static final String TAG="TclUnifiedHost";
 private final Context host;
 private final Handler main=new Handler(Looper.getMainLooper());
 private final ThreadPoolExecutor queue=new ThreadPoolExecutor(0,1,10,TimeUnit.SECONDS,new LinkedBlockingQueue<Runnable>(),new ThreadFactory(){
  @Override public Thread newThread(Runnable task){Thread t=new Thread(task,"tcl-one-controller");t.setDaemon(true);return t;}
 });
 private BroadcastReceiver appReceiver,debugReceiver,packageReceiver;
 private Object engine;
 private String sourcePath;
 private volatile long heartbeat;
 private volatile boolean disposed,updating;
 private volatile boolean checkQueued;
 private boolean watching;
 private final Runnable health=new Runnable(){@Override public void run(){
  if(disposed||!watching)return;
  if(!checkQueued){checkQueued=true;submit(new Runnable(){@Override public void run(){try{checkOwner();}finally{checkQueued=false;}}});}
  main.postDelayed(this,1000);
 }};
 Controller(Context context)throws Exception {
  host=context;
  if(host==null||Process.myUid()!=1000||!"com.tcl.ttvs".equals(host.getPackageName()))throw new Exception("Unexpected host");
  try(FileInputStream f=new FileInputStream("/proc/self/cmdline")){byte[] v=new byte[512];int n=f.read(v);if(!"com.tcl.ttvs.container".equals(new String(v,0,n,"UTF-8").replace('\0',' ').trim()))throw new Exception("Unexpected process");}
 }
 void register()throws Exception {
  verifyPackage();
  appReceiver=new BroadcastReceiver(){@Override public void onReceive(Context context,Intent intent){
   final String action=intent.getAction().substring(PREFIX.length());
   if("HEARTBEAT".equals(action)){heartbeat=SystemClock.elapsedRealtime();return;}
   submit(new Runnable(){@Override public void run(){handle(action);}});
  }};
  IntentFilter commands=new IntentFilter();
  for(String action:new String[]{"START","STOP","CONFIG","STATUS","POWER_ON","POWER_OFF","PAUSE","HEARTBEAT"})commands.addAction(PREFIX+action);
  host.registerReceiver(appReceiver,commands,PERM,null,Context.RECEIVER_EXPORTED);
  debugReceiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){
   final String action=i.getAction().substring((PREFIX+"DEBUG_").length());
   final String config=i.getStringExtra("config");
   submit(new Runnable(){@Override public void run(){
    if("RETIRE_LEGACY".equals(action)){try{LegacyRetirer.retire(host);publish("legacy_retired",null);}catch(Throwable e){error(e);}return;}
    try{
     if("START".equals(action))apply("{\"enabled\":true,\"power\":true}");
     else if("STOP".equals(action))apply("{\"enabled\":false}");
     else if("CONFIG".equals(action))apply(config==null?"{}":config);
     else if("POWER_OFF".equals(action)||"EXIT".equals(action))apply("{\"enabled\":false,\"power\":false}");
     else if("POWER_ON".equals(action))apply("{\"power\":true}");
    }catch(Throwable e){error(e);return;}
    handle(action);
   }});
  }};
  IntentFilter debug=new IntentFilter();for(String action:new String[]{"STATUS","START","STOP","CONFIG","POWER_ON","POWER_OFF","RELOAD","RETIRE_LEGACY","EXIT"})debug.addAction(PREFIX+"DEBUG_"+action);
  host.registerReceiver(debugReceiver,debug,"android.permission.DUMP",null,Context.RECEIVER_EXPORTED);
  packageReceiver=new BroadcastReceiver(){@Override public void onReceive(Context context,Intent intent){
   if(intent.getData()==null||!PKG.equals(intent.getData().getSchemeSpecificPart()))return;
   final String action=intent.getAction();final boolean replacing=intent.getBooleanExtra(Intent.EXTRA_REPLACING,false);
   if(Intent.ACTION_PACKAGE_REMOVED.equals(action)&&replacing){updating=true;heartbeat=SystemClock.elapsedRealtime()+15000;return;}
   submit(new Runnable(){@Override public void run(){
    try{
     if(Intent.ACTION_PACKAGE_REMOVED.equals(action)){shutdown("uninstalled");}
     else if(Intent.ACTION_PACKAGE_REPLACED.equals(action)){updating=false;reload();requestOwnerService();}
     else if(Intent.ACTION_PACKAGE_RESTARTED.equals(action)||Intent.ACTION_PACKAGE_DATA_CLEARED.equals(action)){stopEngine();power(false);publish("owner_stopped",null);}
    }catch(Throwable error){error(error);}
   }});
  }};
  IntentFilter packages=new IntentFilter();packages.addDataScheme("package");
  for(String action:new String[]{Intent.ACTION_PACKAGE_REMOVED,Intent.ACTION_PACKAGE_REPLACED,Intent.ACTION_PACKAGE_RESTARTED,Intent.ACTION_PACKAGE_DATA_CLEARED})packages.addAction(action);
  host.registerReceiver(packageReceiver,packages,null,null,Context.RECEIVER_EXPORTED);
  lease(true);
  JSONObject ready=new JSONObject();ready.put("package",PKG);ready.put("single_apk",true);ready.put("sampling_active",false);publish("ready",ready);
 }
 private void submit(Runnable task){if(!disposed)try{queue.execute(task);}catch(RejectedExecutionException ignored){}}
 private void handle(String action){
  try{
   if(disposed)return;
   if("STATUS".equals(action)){publish("status",null);return;}
   if("STOP".equals(action)||"PAUSE".equals(action)){stopEngine();if("STOP".equals(action)&&installed()&&!stopped())power(settings().optBoolean("power",true));publish("stopped",null);return;}
   if("POWER_OFF".equals(action)||"EXIT".equals(action)){stopEngine();power(false);publish("power_off",null);return;}
   if("POWER_ON".equals(action)){power(true);publish("power_on",null);return;}
   if("RELOAD".equals(action)){reload();return;}
   JSONObject settings=settings();
   if("CONFIG".equals(action)){if(engine!=null)invoke("configure",new Class<?>[]{String.class},config(settings));publish("configured",null);return;}
   if("START".equals(action)){
    heartbeat=SystemClock.elapsedRealtime();
    if(!settings.optBoolean("enabled")||!settings.optBoolean("power",true)){stopEngine();publish("disabled",null);return;}
    verifyPackage();
    String path=host.getPackageManager().getApplicationInfo(PKG,0).sourceDir;
    if(engine==null||!path.equals(sourcePath))load();
    invoke("configure",new Class<?>[]{String.class},config(settings));
    invoke("start",new Class<?>[]{});watching=true;main.removeCallbacks(health);main.postDelayed(health,1000);
    publish("running",null);return;
   }
   throw new Exception("Unsupported fixed action");
  }catch(Throwable error){error(error);}
 }
 private void load()throws Exception {
  if(engine!=null&&!stopEngine())throw new Exception("Old engine did not release resources");
  verifyPackage();ApplicationInfo app=host.getPackageManager().getApplicationInfo(PKG,0);
  if(!app.sourceDir.startsWith("/data/app/")||!app.sourceDir.endsWith("/base.apk"))throw new Exception("Unexpected package path");
  DexClassLoader loader=new DexClassLoader(app.sourceDir,host.getCodeCacheDir().getAbsolutePath(),null,host.getClassLoader());
  Class<?> type=Class.forName(PKG+".tcl.host.LiveEngine",true,loader);
  if(type.getClassLoader()!=loader)throw new Exception("Old parent engine returned");
  engine=type.getConstructor(Context.class).newInstance(host);sourcePath=app.sourceDir;
  JSONObject info=new JSONObject();info.put("new_loader",System.identityHashCode(loader));info.put("version",host.getPackageManager().getPackageInfo(PKG,0).getLongVersionCode());info.put("apk_sha256",fileHash(app.sourceDir));publish("engine_loaded",info);
 }
 private void reload()throws Exception {boolean enabled=installed()&&!stopped()&&settings().optBoolean("enabled");stopEngine();load();if(enabled){heartbeat=SystemClock.elapsedRealtime()+8000;handle("START");}publish("reloaded",null);}
 private Object invoke(String name,Class<?>[]types,Object...args)throws Exception{return engine.getClass().getMethod(name,types).invoke(engine,args);}
 private boolean stopEngine()throws Exception {
  watching=false;main.removeCallbacks(health);
  if(engine==null)return true;
  boolean stopped=(Boolean)invoke("stop",new Class<?>[]{long.class},15000L);
  if(!stopped)throw new Exception("Engine stop timed out; resources not declared clean");
  return true;
 }
 private JSONObject settings()throws Exception {
  if(!installed()||stopped())return new JSONObject("{\"enabled\":false,\"power\":false,\"period_ms\":200,\"brightness_percent\":25}");
  Bundle value=host.getContentResolver().call(STATE,"settings",null,null);
  return new JSONObject(value.getString("json"));
 }
 private void apply(String text)throws Exception {
  if(text.length()>2048)throw new Exception("App settings too long");Bundle input=new Bundle();input.putString("json",text);
  host.getContentResolver().call(STATE,"apply",null,input);
 }
 private String config(JSONObject settings)throws Exception {
  int period=settings.optInt("period_ms",200),brightness=settings.optInt("brightness_percent",25);
  if(period<150||period>2000||brightness<1||brightness>100)throw new Exception("Invalid app configuration");
  JSONObject result=new JSONObject();result.put("period_ms",period);result.put("brightness_percent",brightness);return result.toString();
 }
 private void checkOwner(){
  try{
   if(!installed()){shutdown("missing_package");return;}
   if(!updating&&(stopped()||SystemClock.elapsedRealtime()-heartbeat>7000)){stopEngine();power(false);publish("owner_heartbeat_lost",null);}
   else if(engine!=null&&!new JSONObject((String)invoke("status",new Class<?>[]{})).optBoolean("running")){watching=false;main.removeCallbacks(health);publish("engine_idle",null);}
  }catch(Throwable error){error(error);}
 }
 private boolean installed(){try{host.getPackageManager().getApplicationInfo(PKG,0);return true;}catch(Exception e){return false;}}
 private boolean stopped(){try{return (host.getPackageManager().getApplicationInfo(PKG,0).flags&ApplicationInfo.FLAG_STOPPED)!=0;}catch(Exception e){return true;}}
 private void power(boolean value)throws Exception {StripSession.setPower(host,value);}
 private String fileHash(String path)throws Exception {MessageDigest hash=MessageDigest.getInstance("SHA-256");try(FileInputStream input=new FileInputStream(path)){byte[] bytes=new byte[8192];int n;while((n=input.read(bytes))>=0)hash.update(bytes,0,n);}StringBuilder result=new StringBuilder();for(byte v:hash.digest())result.append(String.format("%02x",v&255));return result.toString();}
 private void requestOwnerService(){try{if(settings().optBoolean("enabled")){Intent i=new Intent("com.xiaomi.mitv.light.action.START_FLOW");i.setComponent(new ComponentName(PKG,PKG+".service.TvLightService"));host.startForegroundService(i);}}catch(Throwable e){error(e);}}
 private void verifyPackage()throws Exception {
  PackageInfo p=host.getPackageManager().getPackageInfo(PKG,PackageManager.GET_SIGNING_CERTIFICATES);
  Signature[] s=p.signingInfo.getApkContentsSigners();if(s.length!=1)throw new Exception("Unexpected signers");
  StringBuilder hash=new StringBuilder();for(byte v:MessageDigest.getInstance("SHA-256").digest(s[0].toByteArray()))hash.append(String.format("%02x",v&255));
  if(!CERT.equals(hash.toString()))throw new Exception("Wrong signing identity");
 }
 private void lease(boolean value)throws Exception {
  Intent i=new Intent("com.tcl.ttvs.businessproxy.action.KeepAlive");i.setComponent(new ComponentName("com.tcl.ttvs","com.tcl.ttvs.businessproxy.core.service.BusinessProxyService"));i.putExtra("isKeepAlive",value);
  if(host.startService(i)==null)throw new Exception("Lease request failed");
 }
 private void shutdown(String reason)throws Exception {
  if(disposed)return;disposed=true;watching=false;main.removeCallbacksAndMessages(null);
  boolean released=stopEngine();
  try{power(false);}catch(Throwable e){Log.w(TAG,"Power-off during cleanup",e);}
  JSONObject finalState=new JSONObject();finalState.put("reason",reason);finalState.put("sampling_stopped",released);
  finalState.put("own_usb_fds",usbFds());
  for(BroadcastReceiver r:new BroadcastReceiver[]{appReceiver,debugReceiver,packageReceiver})if(r!=null)try{host.unregisterReceiver(r);}catch(Exception ignored){}
  try{Class<?> cache=Class.forName("f.f.j.g.a.c.a",false,host.getClassLoader());java.util.Map<?,?> entries=LegacyRetirer.cacheMap(cache);synchronized(entries){entries.remove(PKG);}finalState.put("own_host_cache_removed",true);}catch(Throwable e){finalState.put("cache_remove_error",e.toString());}
  engine=null;sourcePath=null;finalState.put("receivers_unregistered",true);publish("cleanup_complete",finalState);
  queue.shutdown();lease(false);
 }
 private int usbFds(){int count=0;try{java.io.File[] files=new java.io.File("/proc/self/fd").listFiles();if(files==null)return -1;for(java.io.File f:files)if(f.getCanonicalPath().startsWith("/dev/bus/usb/"))count++;}catch(Exception ignored){return -1;}return count;}
 private void error(Throwable error){Throwable e=error;for(int i=0;i<5&&e.getCause()!=null;i++)e=e.getCause();JSONObject j=new JSONObject();try{j.put("error",e.toString());}catch(Exception ignored){}publish("error",j);}
 private void publish(String event,JSONObject info){
  try{
   JSONObject state=info==null?new JSONObject():info;state.put("event",event);state.put("uid",Process.myUid());state.put("pid",Process.myPid());
   state.put("ready",!disposed);state.put("single_apk",true);state.put("running",false);
   state.put("own_usb_fds",usbFds());int sampleThreads=0;for(Thread thread:Thread.getAllStackTraces().keySet())if(thread.isAlive()&&"reloadable-led-engine".equals(thread.getName()))sampleThreads++;state.put("sampling_threads",sampleThreads);
   if(engine!=null){JSONObject e=new JSONObject((String)invoke("status",new Class<?>[]{}));state.put("engine",e);state.put("running",e.optBoolean("running"));}
   Log.i(TAG,state.toString());
   if(!disposed&&installed()&&!stopped()){Bundle b=new Bundle();b.putString("json",state.toString());host.getContentResolver().call(STATE,"report",null,b);}
  }catch(Throwable e){Log.e(TAG,"Publish status",e);}
 }
}
