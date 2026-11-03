package frameluma.core;

/** A verified identity/configuration mismatch will not be repaired by reopening USB repeatedly. */
public final class UnsupportedLightException extends Exception {
    public UnsupportedLightException(String message) {
        super(message);
    }
}
