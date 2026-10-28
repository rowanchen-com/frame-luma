package com.xiaomi.mitv.light.ui;

/** Pixel-window geometry; independent of panel resolution and Android density overrides. */
public final class CenteredViewport {
    public static final int WIDTH = 900, HEIGHT = 400;
    public final int left, top, width, height;
    public final float unit;
    private CenteredViewport(int windowWidth, int windowHeight) {
        unit = Math.min(windowWidth * .70f / WIDTH, windowHeight * .70f / HEIGHT);
        width = Math.round(WIDTH * unit); height = Math.round(HEIGHT * unit);
        left = (windowWidth - width) / 2; top = (windowHeight - height) / 2;
    }
    public static CenteredViewport fit(int width, int height) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Empty window");
        return new CenteredViewport(width, height);
    }
}
