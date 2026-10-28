package com.xiaomi.mitv.light.service;
import android.app.*;
import android.content.Intent;
import android.os.*;
import com.xiaomi.mitv.light.tcl.AppCore;
import com.xiaomi.mitv.light.tcl.Client;
import com.xiaomi.mitv.light.runtime.LightAccess;
public final class TvLightService extends Service {
 public static final String ACTION_START_FLOW="com.xiaomi.mitv.light.action.START_FLOW";
 public static final String ACTION_STOP_FLOW="com.xiaomi.mitv.light.action.STOP_FLOW";
 public static final String ACTION_PAUSE_FLOW="com.xiaomi.mitv.light.action.PAUSE_FLOW";
 private final Handler handler=new Handler(Looper.getMainLooper());
 private boolean foreground;
 private final Runnable heartbeat=new Runnable(){@Override public void run(){
  if(!AppCore.enabled()||!AppCore.power()){stopOwn();return;}
  Client.send(TvLightService.this,"HEARTBEAT");
  Client.send(TvLightService.this,LightAccess.present(TvLightService.this)?"START":"PAUSE");
  handler.postDelayed(this,2000);
 }};
 @Override public void onCreate(){super.onCreate();AppCore.initialize(this);}
 @Override public int onStartCommand(Intent intent,int flags,int id){
  if(intent!=null&&ACTION_STOP_FLOW.equals(intent.getAction())){Client.send(this,"STOP");stopOwn();return START_NOT_STICKY;}
  if(!AppCore.enabled()||!AppCore.power()){Client.send(this,"STOP");stopOwn();return START_NOT_STICKY;}
  if(Build.VERSION.SDK_INT>=26){NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);nm.createNotificationChannel(new NotificationChannel("tcl_follow","幻彩随影",NotificationManager.IMPORTANCE_LOW));}
  Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"tcl_follow"):new Notification.Builder(this);
  startForeground(31,builder.setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("幻彩随影").setContentText("电视画面随影运行中").setOngoing(true).build());foreground=true;
  handler.removeCallbacks(heartbeat);handler.post(heartbeat);return START_STICKY;
 }
 private void stopOwn(){handler.removeCallbacksAndMessages(null);if(foreground){stopForeground(true);foreground=false;}stopSelf();}
 @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);Client.send(this,"STOP");if(foreground)stopForeground(true);super.onDestroy();}
 @Override public IBinder onBind(Intent intent){return null;}
}
