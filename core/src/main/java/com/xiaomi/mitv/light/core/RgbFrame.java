package com.xiaomi.mitv.light.core;

/** An in-memory RGB buffer owned by its source until the next beginFrame. No file representation. */
public final class RgbFrame {
    public final int width,height;
    private final int[] pixels;
    public RgbFrame(int width,int height,int[] pixels) {
        if(width<1||height<1||pixels==null||(long)width*height!=pixels.length)throw new IllegalArgumentException("Invalid RGB buffer");
        this.width=width;this.height=height;this.pixels=pixels;
    }
    public int rgbAt(int x,int y){if(x<0||x>=width||y<0||y>=height)throw new IllegalArgumentException("Pixel outside frame");return pixels[y*width+x]&0xffffff;}
}
