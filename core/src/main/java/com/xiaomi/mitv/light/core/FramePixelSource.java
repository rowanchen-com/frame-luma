package com.xiaomi.mitv.light.core;

/** Optional full-frame backend contract, including downsampled frame-to-panel coordinate mapping. */
public abstract class FramePixelSource implements PixelSource {
    private RgbFrame frame;
    protected abstract RgbFrame captureFrame() throws Exception;
    @Override public final void beginFrame()throws Exception {frame=captureFrame();if(frame==null)throw new Exception("No frame acquired");}
    @Override public final int readRgb(int x,int y) {
        if(frame==null||x<0||x>=width()||y<0||y>=height())throw new IllegalStateException("No valid current frame/coordinate");
        return frame.rgbAt(Math.min(frame.width-1,(int)((long)x*frame.width/width())),Math.min(frame.height-1,(int)((long)y*frame.height/height())));
    }
    public final RgbFrame currentFrame(){return frame;}
    @Override public void close(){frame=null;}
}
