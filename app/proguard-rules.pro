# External host code loaders isolate our namespace. Preserve names while shrinking code.
-dontobfuscate

# Model backends also execute under a different parent class loader. Do not merge them
# into AndroidX/Kotlin classes whose television versions differ. Dead code can still shrink.
-keep,allowshrinking class frameluma.runtime.** { *; }
-keep,allowshrinking class frameluma.core.** { *; }
-keep,allowshrinking class frameluma.deviceapi.** { *; }
-keep,allowshrinking class frameluma.devices.** { *; }
-keep,allowshrinking class frameluma.layouts.** { *; }
-keep,allowshrinking class frameluma.transports.** { *; }

# Names and public signatures consumed by the model's existing reflective host contract.
-keep,allowoptimization class frameluma.runtime.FollowEngine {
    public <init>(android.content.Context);
    public void configure();
    public void start();
    public boolean stop(long);
    public java.lang.String status();
}
-keep,allowoptimization class frameluma.runtime.HostRuntime {
    public static void attach(android.content.Context, java.util.concurrent.atomic.AtomicReference);
}
# Public module contracts remain stable across the model-owned class loader.
-keep,allowoptimization interface frameluma.deviceapi.DisplayControl { public *; }
-keep,allowoptimization interface frameluma.layouts.LayoutProfile { public *; }
