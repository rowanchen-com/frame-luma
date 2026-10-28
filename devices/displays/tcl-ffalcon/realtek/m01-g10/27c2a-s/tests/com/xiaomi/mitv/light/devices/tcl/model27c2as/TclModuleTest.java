package com.xiaomi.mitv.light.devices.tcl.model27c2as;
import com.xiaomi.mitv.light.core.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class TclModuleTest {
    @Test public void familyNameAloneCannotSelectThisModel() {
        Tcl27C2asModule m=new Tcl27C2asModule(new MatchRule("TCL","M01","G10","C3APRO","rtd2885p","V8-M001T01-LF1V064",32));
        assertFalse(m.matches(new DeviceIdentity("TCL","M01","G10","C3APRO","rtd2885p","V8-M001T01-LF1V064",null)));
        assertTrue(m.matches(new DeviceIdentity("TCL","M01","G10","C3APRO","rtd2885p","V8-M001T01-LF1V064",32)));
    }
    @Test public void untestedInputsRemainPending() {
        CaptureCapabilities c=new Tcl27C2asModule().capabilities();
        assertEquals(CaptureCapabilities.Mode.POINTS,c.mode);assertEquals(3840,c.width);
        assertEquals(Verification.VERIFIED,c.inputs.get("DP"));assertEquals(Verification.PENDING,c.inputs.get("HDMI1"));
        assertEquals(Verification.PENDING,c.inputs.get("HDMI2"));assertEquals(Verification.PENDING,c.inputs.get("internal_android"));
    }
}
