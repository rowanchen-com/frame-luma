package frameluma.deviceapi;

/** A connection candidate. USB bridge identity is not verified controller identity. */
public final class LightEndpoint {
    public final String id, label;

    public LightEndpoint(String id, String label) {
        if (id == null || id.isEmpty() || id.length() > 256 || label == null)
            throw new IllegalArgumentException("Invalid connection candidate");
        this.id = id;
        this.label = label;
    }
}
