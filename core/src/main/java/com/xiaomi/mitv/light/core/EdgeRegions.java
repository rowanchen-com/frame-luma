package com.xiaomi.mitv.light.core;

/** Normalized regions translated into real panel coordinates by the selected backend. */
public final class EdgeRegions {
    private EdgeRegions() {}
    public static int[][][] points(int width,int height,int samples,int depthPercent) {
        if(width<16||height<16||(samples!=1&&samples!=3)||depthPercent<5||depthPercent>30)
            throw new IllegalArgumentException("Invalid sample geometry");
        double d=depthPercent/100.0;
        double[][] boxes={{0,1-d,.5,1},{0,.5,d,1},{0,0,d,.5},
            {0,0,1.0/3,d},{1.0/3,0,2.0/3,d},{2.0/3,0,1,d},
            {1-d,0,1,.5},{1-d,.5,1,1},{.5,1-d,1,1}};
        int[][][] result=new int[9][samples][2];
        for(int z=0;z<9;z++)for(int p=0;p<samples;p++) {
            double f=(p+.5)/samples;double[] b=boxes[z];boolean side=z==1||z==2||z==6||z==7;
            double x=side?(b[0]+b[2])/2:b[0]+(b[2]-b[0])*f;
            double y=side?b[1]+(b[3]-b[1])*f:(b[1]+b[3])/2;
            result[z][p][0]=Math.max(1,Math.min(width-1,(int)Math.round(x*(width-1))));
            result[z][p][1]=Math.max(1,Math.min(height-1,(int)Math.round(y*(height-1))));
        }return result;
    }
}
