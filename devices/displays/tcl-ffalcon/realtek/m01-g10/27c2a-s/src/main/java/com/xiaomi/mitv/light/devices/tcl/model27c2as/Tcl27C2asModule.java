package com.xiaomi.mitv.light.devices.tcl.model27c2as;
import android.content.Context;
import android.os.IBinder;
import android.os.Parcel;
import com.xiaomi.mitv.light.core.*;
import com.xiaomi.mitv.light.deviceapi.DisplayModule;
import com.xiaomi.mitv.light.deviceapi.ModuleAssets;
import java.util.LinkedHashMap;

/** V064-only, reviewed read-only project-id transaction and system pixel SDK. */
public final class Tcl27C2asModule implements DisplayModule {
    private volatile MatchRule rule;
    public Tcl27C2asModule() {}
    Tcl27C2asModule(MatchRule rule){this.rule=rule;}
    @Override public String id(){return "tcl-27c2a-s";}
    @Override public String displayName(){return "TCL 27C2A-S";}
    public static String property(String key)throws Exception {
        return (String)Class.forName("android.os.SystemProperties").getMethod("get",String.class).invoke(null,key);
    }
    @Override public DeviceIdentity inspect(Context context)throws Exception {
        if(rule==null){org.json.JSONObject m=ModuleAssets.descriptor(context,id()).getJSONObject("match");
            rule=new MatchRule(m.getString("brand"),m.getString("product"),m.getString("device"),m.getString("model"),m.getString("hardware"),m.getString("firmware_contains"),m.getInt("project_id"));
        }
        String brand=property("ro.product.brand"),product=property("ro.product.name"),device=property("ro.product.device"),
            model=property("ro.product.model"),hardware=property("ro.hardware"),fp=property("ro.build.fingerprint");
        Integer project=null;
        if(rule.matchesPlatform(new DeviceIdentity(brand,product,device,model,hardware,fp,null))) {
            try{project=projectId();}catch(Exception ignored){/* Unknown remains unknown, never assume Project32. */}
        }
        return new DeviceIdentity(brand,product,device,model,hardware,fp,project);
    }
    private static int projectId()throws Exception {
        IBinder service=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class)
            .invoke(null,"vendor.realtek.rtkconfigs.IRtkProjectConfigs/default");
        if(service==null)throw new Exception("Project configuration service unavailable");
        Parcel request=Parcel.obtain(),reply=Parcel.obtain();
        try {
            request.writeInterfaceToken("vendor.realtek.rtkconfigs.IRtkProjectConfigs");
            if(!service.transact(27,request,reply,0))throw new Exception("Project read rejected");
            reply.readException();if(reply.readInt()!=1)throw new Exception("No project object");
            int size=reply.readInt();if(size!=12||reply.dataAvail()<8)throw new Exception("Unexpected project structure");
            if(reply.readInt()!=1)throw new Exception("Project identifier unspecified");return reply.readInt();
        }finally{request.recycle();reply.recycle();}
    }
    @Override public boolean matches(DeviceIdentity d){return rule!=null&&rule.matches(d);}
    @Override public CaptureCapabilities capabilities(){
        LinkedHashMap<String,Verification> inputs=new LinkedHashMap<>();
        inputs.put("DP",Verification.VERIFIED);inputs.put("HDMI1",Verification.PENDING);
        inputs.put("HDMI2",Verification.PENDING);inputs.put("internal_android",Verification.PENDING);
        return new CaptureCapabilities(CaptureCapabilities.Mode.POINTS,3840,2160,inputs);
    }
    @Override public PixelSource open(Context context,DeviceIdentity d)throws Exception {
        if(!matches(d))throw new Exception("Device/firmware/project does not match this module");
        return new TclPixelSource(context);
    }
}
