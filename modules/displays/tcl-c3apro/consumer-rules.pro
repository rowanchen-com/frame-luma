# The display host discovers this entry reflectively; its contract belongs to this adapter.
-keep,allowoptimization class frameluma.devices.tcl.model27c2as.HostService$Entry {
    public <init>();
    public java.lang.String getRequest(android.content.Context);
    public void onResponse(android.content.Context, java.lang.String);
}
