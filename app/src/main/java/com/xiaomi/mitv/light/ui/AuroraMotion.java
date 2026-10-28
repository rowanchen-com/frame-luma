package com.xiaomi.mitv.light.ui;

/** Reusable motion state for seven overlapping soft lights; clockwise orbit with continuous hue changes. */
public final class AuroraMotion {
    public static final int COUNT=7, PALETTE=24;
    public static final float ORBIT_SECONDS=48f;
    private AuroraMotion(){ }
    public static float x(double seconds,int index) {
        double angle=angle(seconds,index);
        return (float)(96+100*Math.cos(angle));
    }
    public static float y(double seconds,int index) {
        double angle=angle(seconds,index);
        return (float)(54+62*Math.sin(angle));
    }
    private static double angle(double seconds,int index) {
        if(index<0||index>=COUNT)throw new IllegalArgumentException("Unknown aurora light");
        return seconds*(2*Math.PI/ORBIT_SECONDS)+index*(2*Math.PI/COUNT);
    }
    public static float hue(double seconds,int index) {
        float hue=(float)((index*(360.0/COUNT)+seconds*7)%360);
        return hue<0?hue+360:hue;
    }
}
