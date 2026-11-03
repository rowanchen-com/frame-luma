package frameluma.deviceapi;

public interface DeviceModule {
    ModuleConfig config();

    default String id() {
        return config().string("id");
    }

    default String displayName() {
        return config().text("display_name");
    }

    final class Info {
        public final String model, brand, manufacturer;

        private Info(String model, String brand, String manufacturer) {
            this.model = model;
            this.brand = brand;
            this.manufacturer = manufacturer;
        }

        public static Info read() {
            return new Info(
                    android.os.Build.MODEL, android.os.Build.BRAND, android.os.Build.MANUFACTURER);
        }
    }
}
