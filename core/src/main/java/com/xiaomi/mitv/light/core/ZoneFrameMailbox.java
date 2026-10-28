package com.xiaomi.mitv.light.core;

/** Ephemeral frame produced by our own visible UI, never a claim of capturing other apps. */
public final class ZoneFrameMailbox {
    public static final long MAX_AGE_MS = 600;
    public static final class Frame {
        public final int[] colors;
        public final long at;
        public final String layout;
        private Frame(int[] colors,long at,String layout) { this.colors=colors.clone();this.at=at;this.layout=layout; }
    }
    private volatile Frame latest;
    public void publish(int[] colors,long at,String layout) {
        if(colors==null||colors.length!=9||layout==null||layout.length()>2048||at<0)
            throw new IllegalArgumentException("Invalid UI zone frame");
        for(int rgb:colors)if(rgb<0||rgb>0xffffff)throw new IllegalArgumentException("Invalid RGB");
        latest=new Frame(colors,at,layout);
    }
    public Frame read(long now) {
        Frame frame=latest;
        if(frame==null||now<frame.at||now-frame.at>MAX_AGE_MS)return null;
        return new Frame(frame.colors,frame.at,frame.layout);
    }
    public void clear(){latest=null;}
}
