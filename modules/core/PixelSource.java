package frameluma.core;

/** A backend reports actual panel pixels, not Android UI coordinates. */
public interface PixelSource extends AutoCloseable {
    int width();

    int height();

    /** Frame-backed implementations acquire once here, not once per requested point. */
    default void beginFrame() throws Exception {}

    int readRgb(int x, int y) throws Exception;

    @Override
    void close();
}
