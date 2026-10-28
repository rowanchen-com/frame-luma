package com.xiaomi.mitv.light.ui;

/** Absolute track coordinates: tapping and dragging to the same point select the same integer. */
public final class PercentTrack {
    private PercentTrack(){ }
    public static int at(float x,int width,int left,int right) {
        int length=width-left-right;
        if(length<=0)throw new IllegalArgumentException("Empty slider track");
        return Math.max(0,Math.min(100,Math.round((x-left)*100f/length)));
    }
}
