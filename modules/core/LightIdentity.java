package frameluma.core;

/** Identity returned by a verified controller handshake, separate from a USB bridge match. */
public final class LightIdentity {
    public final String model;

    public LightIdentity(String model) {
        this.model = model;
    }
}
