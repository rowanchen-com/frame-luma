package frameluma.runtime;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;

public final class StateProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        AppCore.initialize(getContext());
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        int uid = Binder.getCallingUid();
        boolean host =
                uid != Process.myUid() && frameluma.runtime.Devices.acceptsHost(getContext(), uid);
        if (!host && uid != Process.myUid())
            throw new SecurityException("Only owner or verified model host");
        return RuntimeState.call(method, arg, extras);
    }

    @Override
    public void dump(java.io.FileDescriptor fd, java.io.PrintWriter writer, String[] args) {
        // ActivityManager checks DUMP before dispatching this callback to our main thread.
        // There is no remote Binder identity here; checking it would reject our own UID.
        try {
            org.json.JSONObject state = new org.json.JSONObject(AppCore.state());
            state.put("enabled", AppCore.enabled());
            state.put("power", AppCore.power());
            state.put("runtime_uid", Process.myUid());
            state.put("recovery", Recovery.status(getContext()));
            writer.println(state.toString());
        } catch (org.json.JSONException error) {
            writer.println("{\"error\":\"Invalid runtime state\"}");
        }
    }

    @Override
    public Cursor query(Uri u, String[] p, String s, String[] a, String o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getType(Uri u) {
        return "application/json";
    }

    @Override
    public Uri insert(Uri u, ContentValues v) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri u, String s, String[] a) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri u, ContentValues v, String s, String[] a) {
        throw new UnsupportedOperationException();
    }
}

/** Shared fixed operations. Remote callers must pass StateProvider's identity check first. */
final class RuntimeState {
    private RuntimeState() {}

    static Bundle call(String method, String arg, Bundle extras) {
        Bundle result = new Bundle();
        try {
            switch (method) {
                case "settings":
                    synchronized (AppCore.class) {
                        AppCore.expireLocator();
                        String revision = AppCore.configurationToken();
                        result.putString("revision", revision);
                        if (!revision.equals(arg)) result.putString("json", AppCore.settings());
                    }
                    break;
                case "flow":
                    result.putBoolean("enabled", AppCore.enabled() && AppCore.power());
                    break;
                case "resolve_output":
                    AppCore.resolveOutput(arg, extras.getString("module"), extras.getInt("units"));
                    result.putString("json", AppCore.settings());
                    break;
                case "ui_frame":
                    return AppCore.uiFrame();
                case "state":
                    result.putString("json", AppCore.state());
                    result.putLong("at", AppCore.reportedAt());
                    break;
                case "report":
                    AppCore.report(extras.getString("json", "{}"));
                    result.putBoolean("ok", true);
                    break;
                default:
                    throw new SecurityException("Unsupported fixed operation");
            }
        } catch (RuntimeException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalArgumentException(error);
        }
        return result;
    }
}
