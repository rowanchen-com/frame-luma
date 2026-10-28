package com.xiaomi.mitv.light.deviceapi;
import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import org.json.JSONObject;

/** Only descriptors compiled into our installed package are read. */
public final class ModuleAssets {
    private ModuleAssets() {}
    public static JSONObject descriptor(Context context,String id)throws Exception {
        if(!id.matches("[a-z0-9]+(?:-[a-z0-9]+)*"))throw new IllegalArgumentException("Invalid module id");
        Context app=context.getPackageName().equals("com.xiaomi.mitv.light")?context:context.createPackageContext("com.xiaomi.mitv.light",0);
        try(InputStream in=app.getAssets().open("modules/"+id+".json");ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] bytes=new byte[1024];int n;while((n=in.read(bytes))>=0){if(out.size()+n>32768)throw new Exception("Descriptor too large");out.write(bytes,0,n);}
            JSONObject value=new JSONObject(out.toString("UTF-8"));
            if(value.getInt("schema_version")!=1||value.getInt("api_version")!=1||!id.equals(value.getString("id")))throw new Exception("Module contract mismatch");return value;
        }
    }
}
