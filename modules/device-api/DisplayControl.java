package frameluma.deviceapi;

import android.content.Context;

/** A model owns routing and caller checks; shared runtime never names a vendor package. */
public interface DisplayControl {
    enum Command {
        START,
        STOP,
        PAUSE,
        CONFIG,
        STATUS,
        HEARTBEAT
    }

    enum Execution {
        EXTERNAL_HOST,
        APPLICATION
    }

    enum BackgroundMode {
        FOREGROUND_SERVICE,
        ACCESSIBILITY
    }

    default BackgroundMode backgroundMode() {
        return BackgroundMode.FOREGROUND_SERVICE;
    }

    default Execution execution() {
        return Execution.EXTERNAL_HOST;
    }

    boolean available(Context context);

    boolean acceptsCaller(Context context, int uid);

    boolean ownsHostProcess(Context context);

    void hostLease(Context context, boolean enabled) throws Exception;

    void send(Context context, Command command);
}
