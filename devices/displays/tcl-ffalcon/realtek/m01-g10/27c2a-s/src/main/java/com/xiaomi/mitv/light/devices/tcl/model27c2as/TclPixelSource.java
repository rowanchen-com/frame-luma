package com.xiaomi.mitv.light.devices.tcl.model27c2as;
import android.content.Context;
import android.os.Process;
import com.xiaomi.mitv.light.core.PixelSource;
import dalvik.system.PathClassLoader;
import java.io.FileInputStream;
import java.lang.reflect.*;
import java.security.MessageDigest;

final class TclPixelSource implements PixelSource {
    private static final String SDK="/product/framework/vendor.tcl.factory-V1.0-java.jar";
    private static final String HASH="062dca0f76f59bb2eb03d98da6e6cbf8ea69c5c6374b2a347245cf3efb46e1de";
    private final Object service,callback;
    private final Method getter;
    private Field red,green,blue;
    private volatile boolean received,closed;
    private int status,color;
    TclPixelSource(Context host)throws Exception {
        if(Process.myUid()!=1000||!"com.tcl.ttvs".equals(host.getPackageName()))throw new Exception("Verified TCL system host required");
        MessageDigest hash=MessageDigest.getInstance("SHA-256");try(FileInputStream f=new FileInputStream(SDK)){byte[] b=new byte[8192];int n;while((n=f.read(b))>=0)hash.update(b,0,n);}
        StringBuilder text=new StringBuilder();for(byte b:hash.digest())text.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        if(!HASH.equals(text.toString()))throw new Exception("Pixel SDK version not validated");
        ClassLoader loader=new PathClassLoader(SDK,getClass().getClassLoader());
        Class<?> api=Class.forName("vendor.tcl.factory.V1_0.IFactory",true,loader);
        Class<?> cb=Class.forName(api.getName()+"$doGetSystemPixelInfoCallback",true,loader);
        service=api.getMethod("getService",String.class,boolean.class).invoke(null,"default",false);
        if(service==null)throw new Exception("Pixel service unavailable");
        getter=api.getMethod("doGetSystemPixelInfo",int.class,int.class,int.class,int.class,cb);
        callback=Proxy.newProxyInstance(loader,new Class<?>[]{cb},new InvocationHandler(){
            @Override public Object invoke(Object proxy,Method method,Object[] args)throws Throwable {
                if("onValues".equals(method.getName())) {
                    Object value=args[0];status=((Number)args[1]).intValue();
                    if(red==null){red=value.getClass().getField("RCrSum");green=value.getClass().getField("GYSum");blue=value.getClass().getField("BCbSum");}
                    long r=Integer.toUnsignedLong(red.getInt(value)),g=Integer.toUnsignedLong(green.getInt(value)),b=Integer.toUnsignedLong(blue.getInt(value));
                    if(r>255||g>255||b>255)throw new IllegalStateException("Unexpected single-pixel result");
                    color=((int)r<<16)|((int)g<<8)|(int)b;received=true;return null;
                }
                if("hashCode".equals(method.getName()))return System.identityHashCode(proxy);
                if("equals".equals(method.getName()))return args!=null&&proxy==args[0];return "FrameLumaPixelCallback";
            }
        });
    }
    @Override public int width(){return 3840;}
    @Override public int height(){return 2160;}
    @Override public synchronized int readRgb(int x,int y)throws Exception {
        if(closed||x<0||x>=width()||y<0||y>=height())throw new IllegalArgumentException("Invalid pixel request");
        received=false;status=-1;getter.invoke(service,x,y,x,y,callback);
        if(!received||status!=0)throw new Exception("Pixel read failed");return color;
    }
    @Override public void close(){closed=true;}
}
