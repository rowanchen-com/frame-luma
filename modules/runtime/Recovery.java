package frameluma.runtime;

import android.Manifest;
import android.accounts.*;
import android.app.Service;
import android.content.*;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.*;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.util.Log;

import frameluma.deviceapi.DisplayControl;

import org.json.JSONObject;

import java.util.*;
import java.util.concurrent.*;

/**
 * User-controlled recovery: bounded rebind, notification binding and an optional local sync
 * account.
 */
public final class Recovery {
    private static final ScheduledThreadPoolExecutor executor =
            new ScheduledThreadPoolExecutor(
                    1,
                    task -> {
                        Thread thread = new Thread(task, "frame-luma-recovery");
                        thread.setDaemon(true);
                        return thread;
                    });
    private static final long[] RETRIES_MS = {0, 5000, 15000, 30000, 60000};
    private static Context application;
    private static ScheduledFuture<?> pending;
    private static int attempt;
    private static volatile String outcome = "idle";
    private static boolean initialized;
    private static volatile long syncCallbacks;
    private static volatile Listener notificationListener;
    private static long notificationRequestedAt;

    private Recovery() {}

    public static synchronized void initialize(Context context) {
        if (initialized) return;
        initialized = true;
        application = context.getApplicationContext();
        executor.setRemoveOnCancelPolicy(true);
        application
                .getContentResolver()
                .registerContentObserver(
                        Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
                        false,
                        new android.database.ContentObserver(new Handler(Looper.getMainLooper())) {
                            public void onChange(boolean self) {
                                request(application, "binding changed");
                            }
                        });
        request(application, "process started");
    }

    private static android.content.SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("frame_luma_recovery", Context.MODE_PRIVATE);
    }

    private static JSONObject policy(Context context) {
        try {
            JSONObject value = Devices.display(context).config().json().optJSONObject("recovery");
            return value == null ? new JSONObject() : value;
        } catch (Exception error) {
            return new JSONObject();
        }
    }

    public static boolean option(Context context, String name) {
        return preferences(context).getBoolean(name, policy(context).optBoolean(name, false));
    }

    public static void option(Context context, String name, boolean enabled) {
        if (!Arrays.asList("enabled", "notification", "sync", "keys", "force_stop", "foreground")
                .contains(name)) throw new IllegalArgumentException("Unknown recovery option");
        preferences(context).edit().putBoolean(name, enabled).apply();
        BackgroundService.refreshKeys();
        request(context, "option changed");
        executor.execute(() -> syncAccount(context));
    }

    private static boolean selected(Context context) {
        try {
            return Devices.control(context).backgroundMode()
                    == DisplayControl.BackgroundMode.ACCESSIBILITY;
        } catch (Exception error) {
            return false;
        }
    }

    public static boolean supported(Context context) {
        return selected(context);
    }

    private static boolean desired(Context context) {
        if (!selected(context)
                || !option(context, "enabled")
                || !AppCore.enabled()
                || !AppCore.power()) return false;
        if (!context.getSystemService(UserManager.class).isUserUnlocked()) return false;
        if (option(context, "force_stop")) return true;
        try {
            // Application's startup copy can retain FLAG_STOPPED after a later explicit launch.
            // Read the current package state when deciding whether recovery is permitted.
            return (context.getPackageManager()
                                    .getApplicationInfo(context.getPackageName(), 0)
                                    .flags
                            & ApplicationInfo.FLAG_STOPPED)
                    == 0;
        } catch (PackageManager.NameNotFoundException removed) {
            return false;
        }
    }

    public static synchronized void request(Context context, String reason) {
        if (application == null) return;
        if (pending != null && !pending.isDone()) return;
        attempt = 0;
        outcome = reason;
        pending = executor.schedule(Recovery::check, 0, TimeUnit.MILLISECONDS);
    }

    private static void check() {
        Context context = application;
        if (context == null) return;
        try {
            syncAccount(context);
            updateNotificationBinding(context);
            if (!desired(context)) {
                outcome = "recovery disabled or lighting stopped";
                return;
            }
            if (BackgroundService.connected()) {
                outcome = "bound";
                return;
            }
            if (context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                    != PackageManager.PERMISSION_GRANTED) {
                outcome = "needs ADB WRITE_SECURE_SETTINGS authorization";
                return;
            }
            appendBinding(context);
            if (attempt == 2) {
                removeBinding(context);
                executor.schedule(
                        () -> {
                            if (desired(context)) appendBinding(context);
                        },
                        400,
                        TimeUnit.MILLISECONDS);
            }
            outcome = "waiting for system binding, attempt " + attempt;
        } catch (Exception error) {
            outcome = "recovery failed: " + error.getClass().getSimpleName();
            Log.w("FrameLumaRecovery", outcome, error);
        } finally {
            synchronized (Recovery.class) {
                pending = null;
                if (desired(context)
                        && !BackgroundService.connected()
                        && ++attempt < RETRIES_MS.length)
                    pending =
                            executor.schedule(
                                    Recovery::check, RETRIES_MS[attempt], TimeUnit.MILLISECONDS);
            }
        }
    }

    private static List<String> bindings(Context context) {
        String value =
                Settings.Secure.getString(
                        context.getContentResolver(),
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        List<String> result = new ArrayList<>();
        if (value != null)
            for (String entry : value.split(":")) if (!entry.isEmpty()) result.add(entry);
        return result;
    }

    private static String component(Context context) {
        return new ComponentName(context, BackgroundService.class).flattenToString();
    }

    private static void appendBinding(Context context) {
        List<String> values = bindings(context);
        String own = component(context);
        boolean found = false;
        for (String value : values) {
            ComponentName parsed = ComponentName.unflattenFromString(value);
            if (parsed != null && own.equals(parsed.flattenToString())) found = true;
        }
        if (!found) {
            values.add(own);
            Settings.Secure.putString(
                    context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    String.join(":", values));
        }
        if (Settings.Secure.getInt(
                        context.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 0)
                != 1)
            Settings.Secure.putInt(
                    context.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
    }

    private static void removeBinding(Context context) {
        List<String> values = bindings(context);
        String own = component(context);
        boolean changed =
                values.removeIf(
                        value -> {
                            ComponentName parsed = ComponentName.unflattenFromString(value);
                            return parsed != null && own.equals(parsed.flattenToString());
                        });
        if (changed)
            Settings.Secure.putString(
                    context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    String.join(":", values));
    }

    private static void syncAccount(Context context) {
        if (!context.getSystemService(UserManager.class).isUserUnlocked() || !selected(context))
            return;
        try {
            AccountManager accounts = AccountManager.get(context);
            String type = context.getPackageName();
            Account[] existing = accounts.getAccountsByType(type);
            if (!desired(context) || !option(context, "sync")) {
                for (Account account : existing) accounts.removeAccountExplicitly(account);
                return;
            }
            Account account = existing.length == 0 ? new Account("FrameLuma", type) : existing[0];
            if (existing.length == 0 && !accounts.addAccountExplicitly(account, null, null)) return;
            if (ContentResolver.getIsSyncable(account, "settings") <= 0)
                ContentResolver.setIsSyncable(account, "settings", 1);
            if (!ContentResolver.getSyncAutomatically(account, "settings"))
                ContentResolver.setSyncAutomatically(account, "settings", true);
        } catch (Exception error) {
            Log.w("FrameLumaRecovery", "Local recovery account unavailable", error);
        }
    }

    private static void updateNotificationBinding(Context context) {
        Listener listener = notificationListener;
        if (!desired(context) || !option(context, "notification")) {
            if (listener != null) listener.requestUnbind();
            return;
        }
        if (listener != null) return;
        ComponentName component = new ComponentName(context, Listener.class);
        if (!context.getSystemService(android.app.NotificationManager.class)
                .isNotificationListenerAccessGranted(component)) return;
        long now = SystemClock.elapsedRealtime();
        if (now - notificationRequestedAt < 5000) return;
        notificationRequestedAt = now;
        NotificationListenerService.requestRebind(component);
    }

    static boolean keys(Context context) {
        return selected(context)
                && option(context, "enabled")
                && option(context, "keys")
                && AppCore.uiVisible();
    }

    public static boolean key(Context context, android.view.KeyEvent event) {
        if (!keys(context)) return false;
        JSONObject mapping = policy(context).optJSONObject("remote_keys");
        String action =
                mapping == null ? "" : mapping.optString(Integer.toString(event.getKeyCode()));
        if (!Arrays.asList("toggle", "on", "off").contains(action)) return false;
        if (event.getAction() == android.view.KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            executor.execute(
                    () -> {
                        boolean enabled =
                                "on".equals(action)
                                        || ("toggle".equals(action) && !AppCore.enabled());
                        if (enabled) AppCore.setEnabled(true);
                        else AppCore.setPower(false);
                        Client.flow(context, enabled);
                    });
        }
        return true;
    }

    public static JSONObject status(Context context) {
        JSONObject value = new JSONObject();
        try {
            value.put("enabled", option(context, "enabled"));
            value.put(
                    "secure_settings",
                    context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                            == PackageManager.PERMISSION_GRANTED);
            value.put("bound", BackgroundService.connected());
            value.put("outcome", outcome);
            value.put("sync_callbacks", syncCallbacks);
            value.put("notification_bound", notificationListener != null);
            value.put("key_filter", keys(context));
            value.put("sync_enabled", option(context, "sync"));
            value.put("force_stop_recovery", option(context, "force_stop"));
            value.put(
                    "notification_access",
                    context.getSystemService(android.app.NotificationManager.class)
                            .isNotificationListenerAccessGranted(
                                    new ComponentName(context, Listener.class)));
        } catch (Exception ignored) {
        }
        return value;
    }

    /** Optional second system binding. Notification payloads are never consumed. */
    public static final class Listener extends NotificationListenerService {
        public void onCreate() {
            super.onCreate();
            AppCore.initialize(this);
            if (option(this, "notification")) request(this, "notification service created");
        }

        public void onListenerConnected() {
            notificationListener = this;
            request(this, "notification service connected");
        }

        public void onListenerDisconnected() {
            if (notificationListener == this) notificationListener = null;
            if (desired(this) && option(this, "notification"))
                requestRebind(new ComponentName(this, Listener.class));
        }

        public void onDestroy() {
            if (notificationListener == this) notificationListener = null;
            super.onDestroy();
        }
    }

    public static final class Sync extends Service {
        private AbstractThreadedSyncAdapter adapter;

        public IBinder onBind(Intent intent) {
            if (adapter == null)
                adapter =
                        new AbstractThreadedSyncAdapter(getApplicationContext(), false) {
                            public void onPerformSync(
                                    Account account,
                                    Bundle extras,
                                    String authority,
                                    ContentProviderClient provider,
                                    SyncResult result) {
                                AppCore.initialize(getContext());
                                syncCallbacks++;
                                request(getContext(), "local settings sync");
                            }
                        };
            return adapter.getSyncAdapterBinder();
        }
    }

    public static final class AccountService extends Service {
        private AbstractAccountAuthenticator authenticator;

        public IBinder onBind(Intent intent) {
            if (authenticator == null)
                authenticator =
                        new AbstractAccountAuthenticator(this) {
                            public Bundle editProperties(AccountAuthenticatorResponse r, String t) {
                                return null;
                            }

                            public Bundle addAccount(
                                    AccountAuthenticatorResponse r,
                                    String t,
                                    String token,
                                    String[] f,
                                    Bundle o) {
                                return null;
                            }

                            public Bundle confirmCredentials(
                                    AccountAuthenticatorResponse r, Account a, Bundle o) {
                                return null;
                            }

                            public Bundle getAuthToken(
                                    AccountAuthenticatorResponse r, Account a, String t, Bundle o) {
                                return null;
                            }

                            public String getAuthTokenLabel(String t) {
                                return null;
                            }

                            public Bundle updateCredentials(
                                    AccountAuthenticatorResponse r, Account a, String t, Bundle o) {
                                return null;
                            }

                            public Bundle hasFeatures(
                                    AccountAuthenticatorResponse r, Account a, String[] f) {
                                Bundle b = new Bundle();
                                b.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, false);
                                return b;
                            }
                        };
            return authenticator.getIBinder();
        }
    }

    /** Explicit advanced options, available only to system/DUMP-authorized ADB callers. */
    public static final class Control extends BroadcastReceiver {
        public void onReceive(Context context, Intent intent) {
            AppCore.initialize(context);
            for (String key :
                    Arrays.asList(
                            "enabled", "notification", "sync", "keys", "force_stop", "foreground"))
                if (intent.hasExtra(key)) option(context, key, intent.getBooleanExtra(key, false));
            if (intent.getBooleanExtra("sync_now", false))
                executor.execute(
                        () -> {
                            syncAccount(context);
                            Account[] accounts =
                                    AccountManager.get(context)
                                            .getAccountsByType(context.getPackageName());
                            if (accounts.length > 0) {
                                Bundle extras = new Bundle();
                                extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
                                ContentResolver.requestSync(accounts[0], "settings", extras);
                            }
                        });
            setResultCode(0);
        }
    }
}
