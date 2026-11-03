package frameluma.deviceapi;

/** Public application identity, generated from the release policy at build time. */
public final class OwnerIdentity {
    private OwnerIdentity() {}

    public static final String PACKAGE = BuildConfig.OWNER_PACKAGE;
    public static final String CERTIFICATE = BuildConfig.OWNER_CERTIFICATE;
}
