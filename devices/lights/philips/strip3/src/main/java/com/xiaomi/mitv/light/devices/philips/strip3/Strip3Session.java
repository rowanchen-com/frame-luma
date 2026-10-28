package com.xiaomi.mitv.light.devices.philips.strip3;
import com.xiaomi.mitv.light.core.LightOutput;
import com.xiaomi.mitv.light.core.LightIdentity;
import android.content.Context;
import android.hardware.usb.*;
import android.os.SystemClock;
import android.util.Log;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

final class Strip3Session implements LightOutput {
        private static final String TAG = "FrameLumaStrip3";
        private Integer originalPower;
        private boolean powerAttempted, ownershipAttempted;
        private int length;
        private long frames;
        private volatile boolean reading;
        private volatile String readFailure;
        private Thread reader;
        private LightIdentity controllerIdentity;
        UsbDeviceConnection connection;
        UsbInterface iface;
        UsbEndpoint input, output;
        Strip3Session(Context context) throws Exception {
            try {
                UsbManager manager = (UsbManager)context.getSystemService(Context.USB_SERVICE);
                UsbDevice target = null; int count = 0;
                for (UsbDevice device : manager.getDeviceList().values()) {
                    if (device.getVendorId() == 0x1a86 && device.getProductId() == 0x7523) { target = device; count++; }
                }
                if (count != 1 || target == null || target.getInterfaceCount() != 1)
                    throw new Exception("Need exactly one known USB bridge");
                iface = target.getInterface(0);
                for (int i = 0; i < iface.getEndpointCount(); i++) {
                    UsbEndpoint endpoint = iface.getEndpoint(i);
                    if (endpoint.getType() == 2 && endpoint.getAddress() == 0x82) input = endpoint;
                    if (endpoint.getType() == 2 && endpoint.getAddress() == 2) output = endpoint;
                }
                if (input == null || output == null) throw new Exception("Unexpected endpoints");
                connection = manager.openDevice(target);
                if (connection == null) throw new Exception("USB open failed");
                if (!connection.claimInterface(iface, false)) throw new Exception("USB interface busy");
                byte[] bridge = new byte[2];
                if (connection.controlTransfer(0xc0, 0x5f, 0, 0, bridge, 2, 1000) != 2 || bridge[1] != 0)
                    throw new Exception("Unexpected bridge reply");
                control(0xa1, 0, 0); baud(); control(0x9a, 0x2518, 0xc3);
                control(0xa1, 0x501f, 0xd90a); baud();
                control(0x9a, 0x2518, 0xc3); control(0xa4, 0xffdf, 0);
                receive(200);
            } catch (Exception error) { close(); throw error; }
        }
        static void power(Context context, boolean value) throws Exception {
            Strip3Session session=new Strip3Session(context);
            try {
                if(!session.exchange("model").contains("philips.light.strip3"))throw new Exception("Unexpected lamp model");
                int current=session.scalar("get_power",0,1);
                if(current!=(value?1:0))session.accept("set_power "+(value?1:0));
            } finally {session.close();}
        }
        public void begin() throws Exception {
            String model = exchange("model"), version = exchange("version");
            LightIdentity detected=Strip3Replies.identity(model,version);
            length = scalar("get_length", 2, 5);
            if(length!=2)throw new Exception("Only the verified 2m strip profile is enabled");
            originalPower = scalar("get_power", 0, 1);
            int linkage = scalar("get_pc_linkage", 0, 1);
            logState("before", originalPower, linkage);
            if(linkage!=0)throw new Exception("Existing PC linkage active; no takeover attempted");
            if (originalPower == 0) {powerAttempted = true; accept("set_power 1");}
            ownershipAttempted = true;
            accept("set_pc_available 1"); accept("set_pc_linkage 1");
            if (scalar("get_pc_linkage", 0, 1) != 1) throw new Exception("Linkage did not enable");
            startReader();
            controllerIdentity=detected;
        }
        @Override public LightIdentity identity(){return controllerIdentity;}
        @Override public int unitCount(){return length*10;}
        @Override public void send(int[] rgb,int sequence,int brightness)throws Exception{
            if(length!=2)throw new Exception("Only the verified 2m profile is enabled");
            String command=Strip3Frames.encode(rgb,sequence,brightness);
            if(readFailure!=null)throw new Exception("Strip frame rejected: "+readFailure);
            write(command);frames++;
        }
        public void end() {
            try {
                stopReader(); // One input owner: release ACKs are read synchronously below.
                if (ownershipAttempted) {
                    safeRelease("set_pc_linkage 0"); safeRelease("set_pc_available 0");
                }
                if (powerAttempted && originalPower != null && originalPower == 0) safeRelease("set_power 0");
                logState("after", scalar("get_power",0,1), scalar("get_pc_linkage",0,1));
            } catch (Throwable error) { logError("cleanup_error", error); }
            finally {close();}
        }
        private void startReader() {
            reading=true;readFailure=null;
            reader=new Thread(() -> {
                byte[] bytes=new byte[128];Strip3Replies replies=new Strip3Replies();
                try {
                    while(reading) {
                        int count=connection.bulkTransfer(input,bytes,bytes.length,100);
                        if(count>0) {
                            String failure=replies.accept(bytes,count);
                            if(failure!=null){readFailure=failure;break;}
                        }
                    }
                }catch(RuntimeException error){if(reading)readFailure=error.toString();}
            },"strip3-usb-replies");
            reader.setDaemon(true);reader.start();
        }
        private void stopReader() {
            reading=false;Thread current=reader;
            if(current==null)return;
            try{current.join(400);}catch(InterruptedException error){Thread.currentThread().interrupt();}
            if(current.isAlive()) {
                // Do not race a still-blocked input owner with synchronous release reads.
                if(connection!=null){connection.close();connection=null;}
                throw new IllegalStateException("USB reply reader did not stop");
            }
            reader=null;
        }
        private void safeRelease(String command) {
            try {accept(command);} catch (Throwable error) {logError("release_error", error);}
        }
        private void logState(String phase, int power, int linkage) {
            try {
                JSONObject state = new JSONObject(); state.put("event","strip_state"); state.put("phase",phase);
                state.put("power",power); state.put("pc_linkage",linkage); state.put("frames_sent",frames);
                state.put("length",length); Log.i(TAG,state.toString());
            } catch(Exception ignored) {}
        }
        private void logError(String event, Throwable error) {
            try {JSONObject value=new JSONObject();value.put("event",event);value.put("error",error.toString());Log.i(TAG,value.toString());}
            catch(Exception ignored) {}
        }
        void control(int request, int value, int index) throws Exception {
            if (connection.controlTransfer(0x40, request, value, index, null, 0, 1000) < 0)
                throw new Exception("CH34 control failed");
        }
        void baud() throws Exception {
            long factor = 1532620800L / 230400; int divisor = 3;
            while (factor > 0xfff0 && divisor > 0) { factor >>= 3; divisor--; }
            factor = 0x10000 - factor; divisor |= 0x80;
            control(0x9a, 0x1312, (int)((factor & 0xff00) | divisor));
            control(0x9a, 0x0f2c, (int)(factor & 255));
        }
        void write(String command) throws Exception {
            byte[] bytes = (command + "\r\n").getBytes(StandardCharsets.US_ASCII);
            if (bytes.length >= 120) throw new Exception("Frame too long");
            if (connection.bulkTransfer(output, bytes, bytes.length, 1000) != bytes.length)
                throw new Exception("Incomplete serial write");
        }
        String receive(int duration) {
            StringBuilder response = new StringBuilder(); byte[] bytes = new byte[128];
            long until = SystemClock.elapsedRealtime() + duration;
            while (SystemClock.elapsedRealtime() < until && response.length() < 2048) {
                int remaining=(int)Math.max(1,until-SystemClock.elapsedRealtime());
                int count = connection.bulkTransfer(input, bytes, bytes.length, Math.min(remaining, 150));
                if (count > 0) response.append(new String(bytes, 0, count, StandardCharsets.US_ASCII));
            }
            return response.toString();
        }
        String exchange(String command) throws Exception {
            // Discard unsolicited/stale replies before starting the next transaction.
            receive(8);
            write(command);
            StringBuilder received=new StringBuilder();byte[] bytes=new byte[128];
            long until=SystemClock.elapsedRealtime()+600;
            while(SystemClock.elapsedRealtime()<until&&received.length()<2048) {
                int remaining=(int)Math.max(1,until-SystemClock.elapsedRealtime());
                int count=connection.bulkTransfer(input,bytes,bytes.length,Math.min(remaining,150));
                if(count>0)received.append(new String(bytes,0,count,StandardCharsets.US_ASCII));
                if(Strip3Replies.complete(command,received.toString()))break;
            }
            String response=received.toString();
            JSONObject entry = new JSONObject(); entry.put("event", "exchange");
            entry.put("tx", command); entry.put("rx", response); Log.d(TAG, entry.toString());
            if (response.contains("err")) throw new Exception("Command rejected: " + command + ": " + response);
            return response;
        }
        void accept(String command) throws Exception {
            String reply = exchange(command);
            if (!reply.matches("(?s).*\\bok\\b.*")) throw new Exception("No ACK: " + command);
        }
        int scalar(String command, int low, int high) throws Exception {
            String response = exchange(command);
            for (String line : response.split("[\\r\\n]+")) {
                if (line.trim().matches("[0-9]+")) {
                    int value = Integer.parseInt(line.trim());
                    if (value >= low && value <= high) return value;
                }
            }
            throw new Exception("No scalar reply: " + command);
        }
        public void close() {
            stopReader();
            if (connection != null) {
                if (iface != null) connection.releaseInterface(iface);
                connection.close(); connection = null;
            }
        }
    }
