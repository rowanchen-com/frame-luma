package frameluma.core;

/** Exclusive duplex byte connection. Drivers own framing; transports own I/O and release. */
public interface BytePort extends AutoCloseable {
    int read(byte[] bytes, int timeoutMs);

    /** Consume exactly length bytes synchronously; the caller reuses its buffer after return. */
    void write(byte[] bytes, int length, int timeoutMs) throws Exception;

    @Override
    void close();
}
