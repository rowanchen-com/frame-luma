package com.xiaomi.mitv.light.tcl.host;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/** Metadata marker. It does not start or bind any other service. */
public final class MarkerService extends Service {
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        stopSelf(startId);
        return START_NOT_STICKY;
    }
}
